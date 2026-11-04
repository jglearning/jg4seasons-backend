package com.jglearning.jg4seasons.auth.token;

import static org.assertj.core.api.Assertions.assertThat;

import com.jglearning.jg4seasons.IntegrationTest;
import com.jglearning.jg4seasons.MutableClock;
import com.jglearning.jg4seasons.TestData;
import com.jglearning.jg4seasons.auth.UserRole;
import java.time.Clock;
import java.time.Duration;
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
}
