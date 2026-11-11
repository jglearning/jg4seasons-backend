package com.jglearning.jg4seasons.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.jglearning.jg4seasons.IntegrationTest;
import com.jglearning.jg4seasons.MutableClock;
import com.jglearning.jg4seasons.TestData;
import com.jglearning.jg4seasons.infra.audio.AudioScope;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** 로그인·refresh 때 음원 범위를 정하는 조회(ADR 0031 결정 6): 학원 사용권 적용 여부 · 현재 반의 권 · 학원 날짜 기준 사용권 상태 */
@IntegrationTest
@Transactional
class AudioScopesTest {

    /** 2026-11-15 12:00 KST */
    static final Instant NOON_KST = Instant.parse("2026-11-15T03:00:00Z");

    @Autowired
    AudioScopes scopes;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    MutableClock clock;

    TestData data;
    long academyId;
    long studentId;

    @BeforeEach
    void setUp() {
        clock.set(NOON_KST);
        data = new TestData(jdbc);
        academyId = data.academy("연계 학원");
        studentId = data.student(academyId, "학생");
    }

    @AfterEach
    void resetClock() {
        clock.reset();
    }

    private AudioScope studentScope() {
        return scopes.of(studentId, UserRole.STUDENT, academyId);
    }

    private long enrolledIn(String bookCode) {
        long classId = data.clazz(academyId, bookCode, bookCode + " 반", TestData.uniqueAccessCode(), "active");
        data.enroll(classId, studentId);
        return classId;
    }

    @Test
    void 직영_학원_학생은_전체() {
        enrolledIn("L2-B1");

        assertThat(studentScope()).isEqualTo(AudioScope.all());
    }

    @Test
    void 연계_학원_학생은_사용_중인_권만() {
        data.enforceLicenses(academyId);
        data.license(academyId, "L2-B1", LocalDate.of(2026, 11, 1), LocalDate.of(2026, 12, 31), 30);
        enrolledIn("L2-B1");

        assertThat(studentScope()).isEqualTo(AudioScope.book("L2-B1"));
    }

    @Test
    void 연계_학원_학생의_권에_사용권이_없으면_없음() {
        data.enforceLicenses(academyId);
        enrolledIn("L2-B1");

        assertThat(studentScope()).isEqualTo(AudioScope.none());
    }

    @Test
    void 사용_시작_전이면_없음() {
        data.enforceLicenses(academyId);
        data.license(academyId, "L2-B1", LocalDate.of(2026, 11, 16), LocalDate.of(2026, 12, 31), 30);
        enrolledIn("L2-B1");

        assertThat(studentScope()).isEqualTo(AudioScope.none());
    }

    @Test
    void 기간이_끝났으면_없음() {
        data.enforceLicenses(academyId);
        data.license(academyId, "L2-B1", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 14), 30);
        enrolledIn("L2-B1");

        assertThat(studentScope()).isEqualTo(AudioScope.none());
    }

    @Test
    void 반이_없는_연계_학원_학생은_없음() {
        data.enforceLicenses(academyId);
        data.license(academyId, "L2-B1", LocalDate.of(2026, 11, 1), LocalDate.of(2026, 12, 31), 30);

        assertThat(studentScope()).isEqualTo(AudioScope.none());
    }

    @Test
    void 사용_중인_권과_끝난_권_반이면_사용_중인_권만() {
        data.enforceLicenses(academyId);
        data.license(academyId, "L2-B1", LocalDate.of(2026, 11, 1), LocalDate.of(2026, 12, 31), 30);
        data.license(academyId, "K-B1", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 14), 30);
        enrolledIn("L2-B1");
        enrolledIn("K-B1");

        assertThat(studentScope()).isEqualTo(AudioScope.book("L2-B1"));
    }

    @Test
    void 사용_중인_권이_둘이면_전체() {
        data.enforceLicenses(academyId);
        data.license(academyId, "L2-B1", LocalDate.of(2026, 11, 1), LocalDate.of(2026, 12, 31), 30);
        data.license(academyId, "K-B1", LocalDate.of(2026, 11, 1), LocalDate.of(2026, 12, 31), 30);
        enrolledIn("L2-B1");
        enrolledIn("K-B1");

        assertThat(studentScope()).isEqualTo(AudioScope.all());
    }

    @Test
    void 떠난_반의_권은_세지_않는다() {
        data.enforceLicenses(academyId);
        data.license(academyId, "L2-B1", LocalDate.of(2026, 11, 1), LocalDate.of(2026, 12, 31), 30);
        long left = enrolledIn("L2-B1");
        data.leave(left, studentId);

        assertThat(studentScope()).isEqualTo(AudioScope.none());
    }

    @Test
    void 종료일은_학원_날짜로_마지막_날까지() {
        data.enforceLicenses(academyId);
        data.license(academyId, "L2-B1", LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 20), 30);
        enrolledIn("L2-B1");

        clock.set(Instant.parse("2026-11-20T14:59:59Z")); // 11/20 23:59:59 KST
        assertThat(studentScope()).isEqualTo(AudioScope.book("L2-B1"));

        clock.set(Instant.parse("2026-11-20T15:00:00Z")); // 11/21 00:00 KST(UTC로는 아직 11/20)
        assertThat(studentScope()).isEqualTo(AudioScope.none());
    }

    @Test
    void 연계_학원_교사_관리자는_전체() {
        data.enforceLicenses(academyId);
        long teacher = data.staff(academyId, "교사", "teacher");
        long admin = data.staff(academyId, "관리자", "admin");

        assertThat(scopes.of(teacher, UserRole.TEACHER, academyId)).isEqualTo(AudioScope.all());
        assertThat(scopes.of(admin, UserRole.ADMIN, academyId)).isEqualTo(AudioScope.all());
    }

    @Test
    void 운영자는_없음() {
        long operator = data.operator("운영자");

        assertThat(scopes.of(operator, UserRole.SUPERADMIN, null)).isEqualTo(AudioScope.none());
    }
}
