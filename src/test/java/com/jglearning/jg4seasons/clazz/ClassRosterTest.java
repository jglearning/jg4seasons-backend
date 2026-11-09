package com.jglearning.jg4seasons.clazz;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jglearning.jg4seasons.IntegrationTest;
import com.jglearning.jg4seasons.TestData;
import com.jglearning.jg4seasons.clazz.api.ActiveClassToday;
import com.jglearning.jg4seasons.clazz.api.ClassLesson;
import com.jglearning.jg4seasons.clazz.api.ClassRef;
import com.jglearning.jg4seasons.clazz.api.ClassRoster;
import com.jglearning.jg4seasons.clazz.api.MoveResult;
import com.jglearning.jg4seasons.infra.web.error.ApiException;
import com.jglearning.jg4seasons.infra.web.error.CommonErrorCode;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** 반 소속 쓰기(등록·이동·퇴원)와 현황용 반 조회. 학원 범위 밖 반은 404, 반은 학원 범위로 다시 거른다(enrollment에 academy_id 없음). */
@IntegrationTest
@Transactional
class ClassRosterTest {

    static final LocalDate TODAY = LocalDate.of(2026, 11, 9);

    @Autowired
    ClassRoster roster;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    EntityManager em;

    TestData data;
    long academyId;
    long classA;
    long classB;
    long classC;

    @BeforeEach
    void setUp() {
        data = new TestData(jdbc);
        academyId = data.academy("소속 학원");
        classA = activeClass(academyId, "A반");
        classB = activeClass(academyId, "B반");
        classC = activeClass(academyId, "C반");
    }

    private long activeClass(long academy, String name) {
        return data.clazz(academy, "L2-B1", name, TestData.uniqueAccessCode(), "active");
    }

    /** 현재 소속 반 id(JDBC로 — flush 뒤) */
    private List<Long> currentClassIds(long studentId) {
        em.flush();
        return jdbc.queryForList(
                "select class_id from enrollment where student_id = ? and left_at is null order by class_id",
                Long.class,
                studentId);
    }

    static void assertCode(Runnable call, Object code) {
        assertThatThrownBy(call::run)
                .isInstanceOfSatisfying(
                        ApiException.class, e -> assertThat(e.errorCode()).isEqualTo(code));
    }

    @Test
    void 등록하면_오늘_날짜로_현재_소속이_생긴다() {
        long s1 = data.student(academyId, "가");
        long s2 = data.student(academyId, "나");

        roster.enroll(academyId, classA, List.of(s1, s2), TODAY);

        assertThat(roster.currentStudentIds(academyId, classA)).containsExactly(s1, s2);
        em.flush();
        assertThat(jdbc.queryForObject("select joined_at from enrollment where student_id = ?", LocalDate.class, s1))
                .isEqualTo(TODAY);
    }

    @Test
    void 끝난_반에는_등록할_수_없다() {
        long finished = data.clazz(academyId, "L2-B1", "끝난 반", TestData.uniqueAccessCode(), "finished");
        long s = data.student(academyId, "가");

        assertCode(() -> roster.enroll(academyId, finished, List.of(s), TODAY), ClassErrorCode.CLASS_NOT_ACTIVE);
    }

    @Test
    void 다른_학원_반은_404() {
        long otherAcademy = data.academy("다른 학원");
        long otherClass = activeClass(otherAcademy, "남의 반");
        long s = data.student(academyId, "가");
        data.enroll(classA, s);

        assertCode(() -> roster.currentStudentIds(academyId, otherClass), CommonErrorCode.NOT_FOUND);
        assertCode(() -> roster.enroll(academyId, otherClass, List.of(s), TODAY), CommonErrorCode.NOT_FOUND);
        assertCode(() -> roster.move(academyId, s, otherClass, TODAY), CommonErrorCode.NOT_FOUND);
        assertThat(currentClassIds(s)).containsExactly(classA);
    }

    @Test
    void 현재_학생은_끝난_소속을_빼고_id_순() {
        long s1 = data.student(academyId, "가");
        long s2 = data.student(academyId, "나");
        data.enroll(classA, s2);
        data.enroll(classA, s1);
        roster.leaveAll(academyId, s2, TODAY);

        assertThat(roster.currentStudentIds(academyId, classA)).containsExactly(s1);
    }

    @Test
    void 이동하면_현재_소속이_모두_끝나고_새_반_하나만_남는다() {
        long s = data.student(academyId, "가");
        data.enroll(classA, s);
        data.enroll(classB, s);

        MoveResult result = roster.move(academyId, s, classC, TODAY);

        assertThat(result.fromClassIds()).containsExactly(classA, classB);
        assertThat(result.toClassId()).isEqualTo(classC);
        assertThat(currentClassIds(s)).containsExactly(classC);
        assertThat(jdbc.queryForList(
                        "select left_at from enrollment where student_id = ? and class_id in (?, ?)",
                        LocalDate.class,
                        s,
                        classA,
                        classB))
                .containsOnly(TODAY);
    }

    @Test
    void A와_B에_있고_B로_이동하면_A만_끝난다() {
        long s = data.student(academyId, "가");
        data.enroll(classA, s);
        data.enroll(classB, s);

        MoveResult result = roster.move(academyId, s, classB, TODAY);

        assertThat(result.fromClassIds()).containsExactly(classA);
        assertThat(currentClassIds(s)).containsExactly(classB);
        // B 소속은 새로 만들지 않고 그대로(가입일 유지)
        assertThat(jdbc.queryForObject(
                        "select count(*) from enrollment where student_id = ? and class_id = ?",
                        Integer.class,
                        s,
                        classB))
                .isEqualTo(1);
    }

    @Test
    void 이미_그_반에만_있으면_409_ALREADY_IN_CLASS() {
        long s = data.student(academyId, "가");
        data.enroll(classA, s);

        assertCode(() -> roster.move(academyId, s, classA, TODAY), ClassErrorCode.ALREADY_IN_CLASS);
        assertThat(currentClassIds(s)).containsExactly(classA);
    }

    @Test
    void 끝난_반으로는_이동할_수_없다() {
        long finished = data.clazz(academyId, "L2-B1", "끝난 반", TestData.uniqueAccessCode(), "finished");
        long s = data.student(academyId, "가");
        data.enroll(classA, s);

        assertCode(() -> roster.move(academyId, s, finished, TODAY), ClassErrorCode.CLASS_NOT_ACTIVE);
        assertThat(currentClassIds(s)).containsExactly(classA);
    }

    @Test
    void 소속이_없는_학생도_이동으로_반에_들어간다() {
        long s = data.student(academyId, "가");

        MoveResult result = roster.move(academyId, s, classA, TODAY);

        assertThat(result.fromClassIds()).isEmpty();
        assertThat(currentClassIds(s)).containsExactly(classA);
    }

    @Test
    void 퇴원_처리로_모든_소속이_끝나고_끝낸_반을_돌려준다() {
        long s = data.student(academyId, "가");
        data.enroll(classB, s);
        data.enroll(classA, s);

        List<Long> left = roster.leaveAll(academyId, s, TODAY);

        assertThat(left).containsExactly(classA, classB);
        assertThat(currentClassIds(s)).isEmpty();
        assertThat(roster.isCurrentMemberOfAny(academyId, s)).isFalse();
    }

    @Test
    void 다른_학원_반_소속은_퇴원으로_끝내지_않는다() {
        // 데이터 오류(학생이 다른 학원 반에 소속)여도 그 학원 범위 밖은 건드리지 않는다
        long otherClass = activeClass(data.academy("다른 학원"), "남의 반");
        long s = data.student(academyId, "가");
        data.enroll(otherClass, s);

        assertThat(roster.leaveAll(academyId, s, TODAY)).isEmpty();
        assertThat(currentClassIds(s)).containsExactly(otherClass);
    }

    @Test
    void 학생별_현재_반은_id가_가장_작은_반_소속_없으면_빠진다() {
        long s1 = data.student(academyId, "가");
        long s2 = data.student(academyId, "나");
        data.enroll(classB, s1);
        data.enroll(classA, s1);

        Map<Long, ClassRef> found = roster.currentClassOf(academyId, List.of(s1, s2));

        assertThat(found).containsOnlyKeys(s1);
        assertThat(found.get(s1)).isEqualTo(new ClassRef(classA, "A반"));
        assertThat(roster.currentClassOf(academyId, List.of())).isEmpty();
    }

    @Test
    void 담당_반_학생이면_isTaughtBy_참_다른_반이면_거짓() {
        long teacher = data.staff(academyId, "김교사", "teacher");
        data.assignTeacher(classA, teacher);
        long mine = data.student(academyId, "가");
        long notMine = data.student(academyId, "나");
        data.enroll(classA, mine);
        data.enroll(classB, notMine);

        assertThat(roster.isTaughtBy(academyId, mine, teacher)).isTrue();
        assertThat(roster.isTaughtBy(academyId, notMine, teacher)).isFalse();
    }

    @Test
    void 끝난_소속은_isTaughtBy_거짓() {
        long teacher = data.staff(academyId, "김교사", "teacher");
        data.assignTeacher(classA, teacher);
        long s = data.student(academyId, "가");
        data.enroll(classA, s);
        roster.move(academyId, s, classB, TODAY);

        assertThat(roster.isTaughtBy(academyId, s, teacher)).isFalse();
        assertThat(roster.isCurrentMemberOfAny(academyId, s)).isTrue();
    }

    @Test
    void activeClassesToday는_active_반만_오늘_회차와_현재_학생을_준다() {
        long finished = data.clazz(academyId, "L2-B1", "끝난 반", TestData.uniqueAccessCode(), "finished");
        data.session(classA, 1, "L2-B1-01", TODAY.minusDays(2), "scheduled");
        data.session(classA, 2, "L2-B1-02", TODAY, "scheduled");
        data.session(classA, 3, "L2-B1-03", TODAY.plusDays(2), "scheduled");
        data.session(classB, 1, "L2-B1-01", TODAY.plusDays(1), "scheduled"); // 개강 전
        long s = data.student(academyId, "가");
        data.enroll(classA, s);
        data.enroll(finished, s);

        List<ActiveClassToday> found = roster.activeClassesToday(academyId, TODAY);

        assertThat(found).extracting(ActiveClassToday::classId).containsExactly(classA, classB, classC);
        ActiveClassToday a = found.get(0);
        assertThat(a.name()).isEqualTo("A반");
        assertThat(a.bookId()).isEqualTo(data.bookId("L2-B1"));
        assertThat(a.today()).map(ClassLesson::seq).contains(2);
        assertThat(a.today()).map(ClassLesson::lessonId).contains(data.lessonId("L2-B1-02"));
        assertThat(a.studentIds()).containsExactly(s);
        assertThat(found.get(1).today()).isEmpty();
        assertThat(found.get(1).studentIds()).isEmpty();
    }
}
