package com.jglearning.jg4seasons.clazz;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jglearning.jg4seasons.IntegrationTest;
import com.jglearning.jg4seasons.TestData;
import com.jglearning.jg4seasons.clazz.api.ClassLesson;
import com.jglearning.jg4seasons.clazz.api.ClassStatus;
import com.jglearning.jg4seasons.clazz.api.TeacherClass;
import com.jglearning.jg4seasons.clazz.api.TeacherClasses;
import com.jglearning.jg4seasons.infra.web.error.ApiException;
import com.jglearning.jg4seasons.infra.web.error.CommonErrorCode;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** 교사 화면(4.1·4.2)이 보는 반: 교사는 담당 반, 관리자는 학원 전체. 진도표는 학생 화면과 같은 접기. */
@IntegrationTest
@Transactional
class TeacherClassesTest {

    static final LocalDate TODAY = LocalDate.of(2026, 11, 10);

    @Autowired
    TeacherClasses teacherClasses;

    @Autowired
    JdbcTemplate jdbc;

    TestData data;
    long academyId;
    long teacherId;
    long mine;
    long notMine;

    @BeforeEach
    void setUp() {
        data = new TestData(jdbc);
        academyId = data.academy("교사 반 학원");
        teacherId = data.staff(academyId, "김교사", "teacher");
        mine = activeClass(academyId, "담당 반");
        notMine = activeClass(academyId, "남의 반");
        data.assignTeacher(mine, teacherId);
    }

    private long activeClass(long academy, String name) {
        return data.clazz(academy, "L2-B1", name, TestData.uniqueAccessCode(), "active");
    }

    private static void assertNotFound(Runnable call) {
        assertThatThrownBy(call::run)
                .isInstanceOfSatisfying(
                        ApiException.class, e -> assertThat(e.errorCode()).isEqualTo(CommonErrorCode.NOT_FOUND));
    }

    @Test
    void 담당_반만_id_순() {
        long second = activeClass(academyId, "담당 반 2");
        data.assignTeacher(second, teacherId);

        List<TeacherClass> found = teacherClasses.activeClasses(academyId, teacherId, false, TODAY);

        assertThat(found).extracting(TeacherClass::classId).containsExactly(mine, second);
        assertThat(found.get(0).name()).isEqualTo("담당 반");
        assertThat(found.get(0).bookId()).isEqualTo(data.bookId("L2-B1"));
        assertThat(found.get(0).status()).isEqualTo(ClassStatus.ACTIVE);
    }

    @Test
    void 관리자는_학원_진행_중_반_전부() {
        long adminId = data.staff(academyId, "관리자", "admin");
        data.clazz(academyId, "L2-B1", "끝난 반", TestData.uniqueAccessCode(), "finished");
        activeClass(data.academy("다른 학원"), "남의 학원 반");

        assertThat(teacherClasses.activeClasses(academyId, adminId, true, TODAY))
                .extracting(TeacherClass::classId)
                .containsExactly(mine, notMine);
    }

    @Test
    void 끝난_반은_목록에_없지만_classOf로는_보인다() {
        long finished = data.clazz(academyId, "L2-B1", "끝난 반", TestData.uniqueAccessCode(), "finished");
        data.assignTeacher(finished, teacherId);

        assertThat(teacherClasses.activeClasses(academyId, teacherId, false, TODAY))
                .extracting(TeacherClass::classId)
                .containsExactly(mine);
        assertThat(teacherClasses
                        .classOf(academyId, teacherId, false, finished, TODAY)
                        .status())
                .isEqualTo(ClassStatus.FINISHED);
    }

    @Test
    void 담당_아닌_반_다른_학원_반_없는_반은_404() {
        long otherAcademy = data.academy("다른 학원");
        long theirs = activeClass(otherAcademy, "남의 학원 반");
        data.assignTeacher(theirs, teacherId); // 데이터가 어긋나도 학원 경계가 먼저

        assertNotFound(() -> teacherClasses.classOf(academyId, teacherId, false, notMine, TODAY));
        assertNotFound(() -> teacherClasses.classOf(academyId, teacherId, false, theirs, TODAY));
        assertNotFound(() -> teacherClasses.classOf(academyId, teacherId, true, theirs, TODAY));
        assertNotFound(() -> teacherClasses.classOf(academyId, teacherId, false, Long.MAX_VALUE, TODAY));
        assertThat(teacherClasses
                        .classOf(academyId, teacherId, true, notMine, TODAY)
                        .classId())
                .as("학원 전체면 담당이 아니어도")
                .isEqualTo(notMine);
    }

    @Test
    void 휴강_시프트_회차는_한_줄이고_done은_대표_행_기준() {
        data.session(mine, 1, "L2-B1-01", TODAY.minusDays(4), "done");
        data.session(mine, 2, "L2-B1-02", TODAY.minusDays(2), "cancelled");
        data.session(mine, 3, "L2-B1-02", TODAY, "scheduled"); // 휴강으로 옮긴 행
        data.session(mine, 4, "L2-B1-03", TODAY.plusDays(2), "scheduled");

        TeacherClass found = teacherClasses.classOf(academyId, teacherId, false, mine, TODAY);

        assertThat(found.lessons()).extracting(ClassLesson::seq).containsExactly(1, 3, 4);
        assertThat(found.lessons().get(1).lessonId()).isEqualTo(data.lessonId("L2-B1-02"));
        assertThat(found.lessons().get(1).reached()).isTrue();
        assertThat(found.lessons().get(2).reached()).isFalse();
        assertThat(found.doneLessonIds()).containsExactly(data.lessonId("L2-B1-01"));
    }

    @Test
    void 저장값이_scheduled여도_지난_대표_행은_done() {
        data.session(mine, 1, "L2-B1-01", TODAY.minusDays(2), "scheduled");
        data.session(mine, 2, "L2-B1-02", TODAY, "scheduled");
        data.session(mine, 3, "L2-B1-03", TODAY.plusDays(2), "scheduled");

        TeacherClass found = teacherClasses.classOf(academyId, teacherId, false, mine, TODAY);

        assertThat(found.doneLessonIds()).containsExactly(data.lessonId("L2-B1-01"));
    }

    @Test
    void 휴강으로_옮긴_회차는_새_행_날짜로() {
        data.session(mine, 1, "L2-B1-01", TODAY.minusDays(2), "cancelled");
        data.session(mine, 2, "L2-B1-01", TODAY, "scheduled"); // 휴강으로 옮긴 행

        TeacherClass found = teacherClasses.classOf(academyId, teacherId, false, mine, TODAY);

        assertThat(found.doneLessonIds()).as("지난 휴강 행이 아니라 오늘로 옮긴 행 기준").isEmpty();
    }

    @Test
    void 휴강_행만_남은_지난_회차는_done이_아니다() {
        data.session(mine, 1, "L2-B1-01", TODAY.minusDays(4), "scheduled");
        data.session(mine, 2, "L2-B1-02", TODAY.minusDays(2), "cancelled"); // 밀지 않은 휴강 — 건너뛴 회차

        TeacherClass found = teacherClasses.classOf(academyId, teacherId, false, mine, TODAY);

        assertThat(found.doneLessonIds()).containsExactly(data.lessonId("L2-B1-01"));
    }

    @Test
    void 떠난_학생은_studentIds에_없다() {
        long stay = data.student(academyId, "남음");
        long left = data.student(academyId, "떠남");
        data.enroll(mine, stay);
        data.enroll(mine, left);
        data.leave(mine, left);

        assertThat(teacherClasses
                        .classOf(academyId, teacherId, false, mine, TODAY)
                        .studentIds())
                .containsExactly(stay);
    }
}
