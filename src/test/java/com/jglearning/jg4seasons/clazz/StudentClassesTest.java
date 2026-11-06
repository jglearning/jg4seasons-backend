package com.jglearning.jg4seasons.clazz;

import static org.assertj.core.api.Assertions.assertThat;

import com.jglearning.jg4seasons.IntegrationTest;
import com.jglearning.jg4seasons.TestData;
import com.jglearning.jg4seasons.clazz.api.ClassLesson;
import com.jglearning.jg4seasons.clazz.api.LessonAccess;
import com.jglearning.jg4seasons.clazz.api.StudentClass;
import com.jglearning.jg4seasons.clazz.api.StudentClasses;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** 학생의 현재 반(현재 소속 active 반 중 id가 가장 작은 것)과 그 반 진도표 기준 회차 열림. */
@IntegrationTest
@Transactional
class StudentClassesTest {

    static final LocalDate NOV_5 = LocalDate.of(2026, 11, 5);

    @Autowired
    StudentClasses studentClasses;

    @Autowired
    JdbcTemplate jdbc;

    TestData data;
    long academyId;
    long studentId;

    @BeforeEach
    void setUp() {
        data = new TestData(jdbc);
        academyId = data.academy("테스트 학원");
        studentId = data.student(academyId, "김학생");
    }

    private long activeClass(String name) {
        return data.clazz(academyId, "L2-B1", name, TestData.uniqueAccessCode(), "active");
    }

    @Test
    void 현재_소속_active_반을_찾는다() {
        long classId = activeClass("겨울 그린 A반");
        data.enroll(classId, studentId);

        assertThat(studentClasses.currentClass(academyId, studentId))
                .contains(new StudentClass(classId, academyId, data.bookId("L2-B1"), "겨울 그린 A반", false));
    }

    @Test
    void 퇴원했으면_없다() {
        long classId = activeClass("겨울 그린 A반");
        data.enroll(classId, studentId);
        data.leave(classId, studentId);

        assertThat(studentClasses.currentClass(academyId, studentId)).isEmpty();
    }

    @Test
    void finished_반은_없다() {
        long classId = data.clazz(academyId, "L2-B1", "끝난 반", TestData.uniqueAccessCode(), "finished");
        data.enroll(classId, studentId);

        assertThat(studentClasses.currentClass(academyId, studentId)).isEmpty();
    }

    @Test
    void 두_반이면_id가_작은_active_반() {
        long finished = data.clazz(academyId, "L2-B1", "끝난 반", TestData.uniqueAccessCode(), "finished");
        long first = activeClass("첫 반");
        long second = activeClass("둘째 반");
        data.enroll(second, studentId);
        data.enroll(first, studentId);
        data.enroll(finished, studentId);

        assertThat(studentClasses.currentClass(academyId, studentId))
                .map(StudentClass::classId)
                .contains(first);
    }

    @Test
    void 다른_학원_id로는_없다() {
        long classId = activeClass("겨울 그린 A반");
        data.enroll(classId, studentId);
        long otherAcademy = data.academy("다른 학원");

        assertThat(studentClasses.currentClass(otherAcademy, studentId)).isEmpty();
    }

    @Test
    void 진도표로_회차_열림을_판정한다() {
        long classId = activeClass("겨울 그린 A반");
        data.enroll(classId, studentId);
        data.session(classId, 1, "L2-B1-01", NOV_5, "scheduled");
        data.session(classId, 2, "L2-B1-02", NOV_5.plusDays(2), "scheduled");
        StudentClass clazz = studentClasses.currentClass(academyId, studentId).orElseThrow();

        assertThat(studentClasses.access(clazz, data.lessonId("L2-B1-01"), NOV_5))
                .isEqualTo(LessonAccess.OPEN);
        assertThat(studentClasses.access(clazz, data.lessonId("L2-B1-02"), NOV_5))
                .isEqualTo(LessonAccess.LOCKED);
    }

    @Test
    void 진도표에_없는_회차는_NOT_IN_CLASS() {
        long classId = activeClass("겨울 그린 A반");
        data.enroll(classId, studentId);
        data.session(classId, 1, "L2-B1-01", NOV_5, "scheduled");
        StudentClass clazz = studentClasses.currentClass(academyId, studentId).orElseThrow();

        assertThat(studentClasses.access(clazz, data.lessonId("L2-B1-03"), NOV_5))
                .as("같은 권, 진도표에 없음")
                .isEqualTo(LessonAccess.NOT_IN_CLASS);
        assertThat(studentClasses.access(clazz, data.lessonId("L3-B1-01"), NOV_5))
                .as("다른 권")
                .isEqualTo(LessonAccess.NOT_IN_CLASS);
    }

    @Test
    void 미래_허용_반은_내일_회차도_열림() {
        long classId = activeClass("겨울 그린 A반");
        jdbc.update("update `class` set allow_future_lessons = true where id = ?", classId);
        data.enroll(classId, studentId);
        data.session(classId, 2, "L2-B1-02", NOV_5.plusDays(1), "scheduled");
        StudentClass clazz = studentClasses.currentClass(academyId, studentId).orElseThrow();

        assertThat(clazz.allowFutureLessons()).isTrue();
        assertThat(studentClasses.access(clazz, data.lessonId("L2-B1-02"), NOV_5))
                .isEqualTo(LessonAccess.OPEN);
    }

    @Test
    void 휴강_뒤_다시_잡힌_회차는_새_수업일에_열림() {
        long classId = activeClass("겨울 그린 A반");
        data.enroll(classId, studentId);
        data.session(classId, 5, "L2-B1-05", LocalDate.of(2026, 11, 3), "cancelled");
        data.session(classId, 6, "L2-B1-05", NOV_5, "scheduled");
        StudentClass clazz = studentClasses.currentClass(academyId, studentId).orElseThrow();
        long lessonId = data.lessonId("L2-B1-05");

        assertThat(studentClasses.access(clazz, lessonId, NOV_5.minusDays(1)))
                .as("휴강일은 지났지만 새 수업일 전")
                .isEqualTo(LessonAccess.LOCKED);
        assertThat(studentClasses.access(clazz, lessonId, NOV_5)).isEqualTo(LessonAccess.OPEN);
    }

    @Test
    void 현재_소속_반을_모두_id_순으로() {
        long first = activeClass("첫 반");
        long second = activeClass("둘째 반");
        long finished = data.clazz(academyId, "L2-B1", "끝난 반", TestData.uniqueAccessCode(), "finished");
        data.enroll(second, studentId);
        data.enroll(finished, studentId);
        data.enroll(first, studentId);

        assertThat(studentClasses.currentClasses(academyId, studentId))
                .extracting(StudentClass::classId, StudentClass::name)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(first, "첫 반"),
                        org.assertj.core.groups.Tuple.tuple(second, "둘째 반"));
        assertThat(studentClasses.currentClasses(data.academy("다른 학원"), studentId))
                .isEmpty();
    }

    private StudentClass enrolledClassWith24Sessions(boolean allowFuture) {
        long classId = activeClass("겨울 그린 A반");
        if (allowFuture) {
            jdbc.update("update `class` set allow_future_lessons = true where id = ?", classId);
        }
        data.enroll(classId, studentId);
        for (int n = 1; n <= 24; n++) {
            data.session(classId, n, String.format("L2-B1-%02d", n), NOV_5.plusDays(n - 5), "scheduled");
        }
        return studentClasses.currentClass(academyId, studentId).orElseThrow();
    }

    @Test
    void 진도표를_회차마다_하나로_접는다() {
        StudentClass clazz = enrolledClassWith24Sessions(false);

        List<ClassLesson> lessons = studentClasses.lessons(clazz, NOV_5);

        assertThat(lessons).hasSize(24);
        assertThat(lessons).extracting(ClassLesson::seq).isSorted();
        ClassLesson fifth = lessons.get(4);
        assertThat(fifth.lessonId()).isEqualTo(data.lessonId("L2-B1-05"));
        assertThat(fifth.scheduledDate()).isEqualTo(NOV_5);
        assertThat(fifth.reached()).as("수업일 당일").isTrue();
        assertThat(fifth.open()).isTrue();
        ClassLesson sixth = lessons.get(5);
        assertThat(sixth.reached()).as("내일").isFalse();
        assertThat(sixth.open()).isFalse();
        assertThat(lessons.get(0).reached()).as("지난 수업").isTrue();
    }

    @Test
    void 미래_허용이면_open이지만_reached는_아님() {
        StudentClass clazz = enrolledClassWith24Sessions(true);

        ClassLesson sixth = studentClasses.lessons(clazz, NOV_5).get(5);

        assertThat(sixth.open()).isTrue();
        assertThat(sixth.reached()).isFalse();
    }

    @Test
    void 휴강_뒤_새_행이_있으면_새_날짜() {
        long classId = activeClass("겨울 그린 A반");
        data.enroll(classId, studentId);
        data.session(classId, 5, "L2-B1-05", NOV_5.minusDays(2), "cancelled");
        data.session(classId, 6, "L2-B1-05", NOV_5, "scheduled");
        StudentClass clazz = studentClasses.currentClass(academyId, studentId).orElseThrow();

        List<ClassLesson> lessons = studentClasses.lessons(clazz, NOV_5.minusDays(1));

        assertThat(lessons).hasSize(1);
        assertThat(lessons.get(0).seq()).isEqualTo(6);
        assertThat(lessons.get(0).scheduledDate()).isEqualTo(NOV_5);
        assertThat(lessons.get(0).reached()).as("휴강일은 지났지만 새 수업일 전").isFalse();
        assertThat(lessons.get(0).held()).isTrue();
        assertThat(lessons.get(0).open()).isFalse();
    }

    @Test
    void 휴강만_있으면_reached_open_모두_아님() {
        long classId = activeClass("겨울 그린 A반");
        data.enroll(classId, studentId);
        data.session(classId, 5, "L2-B1-05", NOV_5.minusDays(2), "cancelled");
        StudentClass clazz = studentClasses.currentClass(academyId, studentId).orElseThrow();

        ClassLesson lesson = studentClasses.lessons(clazz, NOV_5).get(0);

        assertThat(lesson.scheduledDate()).isEqualTo(NOV_5.minusDays(2));
        assertThat(lesson.reached()).isFalse();
        assertThat(lesson.open()).isFalse();
        assertThat(lesson.held()).as("휴강 행만").isFalse();
    }
}
