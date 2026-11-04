package com.jglearning.jg4seasons.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.jglearning.jg4seasons.IntegrationTest;
import com.jglearning.jg4seasons.TestData;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** 개인 코드 인증 수단은 (provider, HMAC 값) 한 번으로 찾고, 사용자는 학원 범위로 다시 읽는다(ADR-22). */
@IntegrationTest
@Transactional
class AuthIdentityRepositoryTest {

    @Autowired
    AuthIdentityRepository identities;

    @Autowired
    UserRepository users;

    @Autowired
    PersonalCodeHasher hasher;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void 개인_코드_HMAC으로_인증_수단을_찾는다() {
        TestData data = new TestData(jdbc);
        long academyId = data.academy("테스트 학원");
        long studentId = data.student(academyId, "학생");
        String identifier = hasher.hash(academyId, "3817");
        identities.save(AuthIdentity.personalCode(studentId, identifier, Instant.parse("2026-11-04T00:00:00Z")));

        assertThat(identities.findByProviderAndIdentifier(AuthProvider.PERSONAL_CODE, identifier))
                .hasValueSatisfying(found -> assertThat(found.getUserId()).isEqualTo(studentId));
        assertThat(identities.existsByUserIdAndProvider(studentId, AuthProvider.PERSONAL_CODE))
                .isTrue();
        assertThat(jdbc.queryForObject(
                        "select identifier from auth_identity where user_id = ?", String.class, studentId))
                .isEqualTo(identifier)
                .doesNotContain("3817");
    }

    @Test
    void 사용자는_학원_범위로만_읽는다() {
        TestData data = new TestData(jdbc);
        long academyId = data.academy("테스트 학원");
        long otherAcademy = data.academy("다른 학원");
        long studentId = data.student(academyId, "학생");

        assertThat(users.findByAcademyIdAndId(academyId, studentId)).hasValueSatisfying(user -> {
            assertThat(user.getName()).isEqualTo("학생");
            assertThat(user.getRole()).isEqualTo(UserRole.STUDENT);
            assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        });
        assertThat(users.findByAcademyIdAndId(otherAcademy, studentId)).isEmpty();
    }
}
