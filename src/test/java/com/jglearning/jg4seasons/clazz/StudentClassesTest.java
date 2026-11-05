package com.jglearning.jg4seasons.clazz;

import static org.assertj.core.api.Assertions.assertThat;

import com.jglearning.jg4seasons.IntegrationTest;
import com.jglearning.jg4seasons.TestData;
import com.jglearning.jg4seasons.clazz.api.LessonAccess;
import com.jglearning.jg4seasons.clazz.api.StudentClass;
import com.jglearning.jg4seasons.clazz.api.StudentClasses;
import java.time.LocalDate;
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
                .contains(new StudentClass(classId, academyId, data.bookId("L2-B1"), false));
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
}
