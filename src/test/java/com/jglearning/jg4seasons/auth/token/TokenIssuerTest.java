package com.jglearning.jg4seasons.auth.token;

import static org.assertj.core.api.Assertions.assertThat;

import com.jglearning.jg4seasons.IntegrationTest;
import com.jglearning.jg4seasons.MutableClock;
import com.jglearning.jg4seasons.TestData;
import com.jglearning.jg4seasons.auth.UserRole;
import com.jglearning.jg4seasons.auth.persistence.AuthUser;
import com.jglearning.jg4seasons.infra.audio.AudioScope;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.transaction.annotation.Transactional;

/** 토큰 묶음(access·refresh·csrf·수명)은 한 곳에서: refresh의 DB 만료와 쿠키 수명이 역할별로 같다. */
@IntegrationTest
@Transactional
class TokenIssuerTest {

    @Autowired
    TokenIssuer tokens;

    @Autowired
    JwtDecoder decoder;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    MutableClock clock;

    long academyId;
    long userId;

    @BeforeEach
    void setUp() {
        clock.set(Clock.systemUTC().instant().truncatedTo(ChronoUnit.SECONDS));
        TestData data = new TestData(jdbc);
        academyId = data.academy("테스트 학원");
        userId = data.student(academyId, "사용자");
    }

    @AfterEach
    void resetClock() {
        clock.reset();
    }

    private LocalDateTime refreshExpiresAt() {
        return jdbc.queryForObject(
                "select expires_at from refresh_token where user_id = ?", LocalDateTime.class, userId);
    }

    @Test
    void 교사_토큰은_refresh가_14일이고_쿠키_수명도_같다() {
        IssuedTokens issued = tokens.issue(userId, UserRole.TEACHER, academyId, "agent");

        assertThat(issued.refreshTtl()).isEqualTo(Duration.ofDays(14));
        assertThat(refreshExpiresAt())
                .isEqualTo(LocalDateTime.ofInstant(clock.instant().plus(Duration.ofDays(14)), ZoneOffset.UTC));
    }

    @Test
    void 학생_토큰은_refresh가_90일() {
        IssuedTokens issued = tokens.issue(userId, UserRole.STUDENT, academyId, null);

        assertThat(issued.refreshTtl()).isEqualTo(Duration.ofDays(90));
        assertThat(refreshExpiresAt())
                .isEqualTo(LocalDateTime.ofInstant(clock.instant().plus(Duration.ofDays(90)), ZoneOffset.UTC));
    }

    @Test
    void access의_role_claim은_소문자_DB_값이다() {
        IssuedTokens issued = tokens.issue(userId, UserRole.ADMIN, academyId, null);

        assertThat(decoder.decode(issued.access()).getClaimAsString("role")).isEqualTo("admin");
        assertThat(issued.csrf()).matches("^[A-Za-z0-9_-]{43}$");
    }

    /** 연계 학원으로 바꾸고 학생을 L2-B1 반에 넣는다(사용권은 늘 기간 안) */
    private void partnerStudentInL2B1() {
        TestData data = new TestData(jdbc);
        data.enforceLicenses(academyId);
        data.license(academyId, "L2-B1", LocalDate.of(2000, 1, 1), LocalDate.of(2999, 12, 31), 30);
        long classId = data.clazz(academyId, "L2-B1", "반", TestData.uniqueAccessCode(), "active");
        data.enroll(classId, userId);
    }

    @Test
    void 로그인_묶음에_음원_범위가_들어간다() {
        partnerStudentInL2B1();
        long operator = new TestData(jdbc).operator("운영자");

        assertThat(tokens.issue(userId, UserRole.STUDENT, academyId, null).audioScope())
                .isEqualTo(AudioScope.book("L2-B1"));
        assertThat(tokens.issue(operator, UserRole.SUPERADMIN, null, null).audioScope())
                .isEqualTo(AudioScope.none());
    }

    @Test
    void refresh_회전_묶음도_같은_범위_규칙() {
        partnerStudentInL2B1();

        IssuedTokens rotated = tokens.afterRotation(
                new AuthUser(userId, academyId, UserRole.STUDENT, "사용자"), "new-refresh", Duration.ofDays(90));

        assertThat(rotated.audioScope()).isEqualTo(AudioScope.book("L2-B1"));
    }
}
