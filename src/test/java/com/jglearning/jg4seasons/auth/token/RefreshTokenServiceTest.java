package com.jglearning.jg4seasons.auth.token;

import static org.assertj.core.api.Assertions.assertThat;

import com.jglearning.jg4seasons.IntegrationTest;
import com.jglearning.jg4seasons.MutableClock;
import com.jglearning.jg4seasons.TestData;
import com.jglearning.jg4seasons.auth.token.RefreshOutcome.GraceReuse;
import com.jglearning.jg4seasons.auth.token.RefreshOutcome.Rejected;
import com.jglearning.jg4seasons.auth.token.RefreshOutcome.Rotated;
import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** refresh: DB에는 해시만, 회전, 유예(10초) 안 재사용은 GraceReuse, 유예 뒤 재사용은 그 사용자 전부 폐기. */
@IntegrationTest
@Transactional
class RefreshTokenServiceTest {

    static final Duration GRACE = Duration.ofSeconds(10);

    @Autowired
    RefreshTokenService refreshTokens;

    @Autowired
    MutableClock clock;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    EntityManager entityManager;

    TestData data;
    long academyId;
    long studentId;

    @BeforeEach
    void setUp() {
        // DB DATETIME은 초 단위(반올림) → 경계 테스트가 흔들리지 않게 초에 맞춘다
        clock.set(Clock.systemUTC().instant().truncatedTo(ChronoUnit.SECONDS));
        data = new TestData(jdbc);
        academyId = data.academy("테스트 학원");
        studentId = data.student(academyId, "학생");
    }

    @AfterEach
    void resetClock() {
        clock.reset();
    }

    static String sha256(String value) throws Exception {
        return HexFormat.of()
                .formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void DB에는_해시만_저장한다() throws Exception {
        String plain = refreshTokens.create(studentId, "test-agent");

        String stored =
                jdbc.queryForObject("select token_hash from refresh_token where user_id = ?", String.class, studentId);
        assertThat(stored).isEqualTo(sha256(plain)).hasSize(64).isNotEqualTo(plain);
        assertThat(plain).as("32바이트 base64url").matches("^[A-Za-z0-9_-]{43}$");
    }

    @Test
    void 회전하면_옛_토큰은_폐기되고_새_토큰이_나온다() throws Exception {
        String plain = refreshTokens.create(studentId, null);

        RefreshOutcome outcome = refreshTokens.rotate(plain);

        assertThat(outcome).isInstanceOf(Rotated.class);
        Rotated rotated = (Rotated) outcome;
        assertThat(rotated.owner().id()).isEqualTo(studentId);
        assertThat(rotated.owner().academyId()).isEqualTo(academyId);
        assertThat(rotated.owner().role()).isEqualTo("student");
        assertThat(rotated.refreshTtl()).isEqualTo(Duration.ofDays(90));
        assertThat(rotated.newRefresh()).isNotEqualTo(plain);
        entityManager.flush(); // 같은 트랜잭션 안에서 JDBC로 읽기 전에 JPA 변경을 내보낸다
        assertThat(jdbc.queryForObject(
                        "select revoked_at is not null from refresh_token where token_hash = ?",
                        Boolean.class,
                        sha256(plain)))
                .isTrue();
        assertThat(refreshTokens.rotate(rotated.newRefresh())).isInstanceOf(Rotated.class);
    }

    @Test
    void 유예_안의_옛_토큰은_GraceReuse() {
        String plain = refreshTokens.create(studentId, null);
        refreshTokens.rotate(plain);

        clock.advance(GRACE);

        RefreshOutcome outcome = refreshTokens.rotate(plain);
        assertThat(outcome).isInstanceOf(GraceReuse.class);
        assertThat(((GraceReuse) outcome).owner().id()).isEqualTo(studentId);
    }

    @Test
    void 회전이_소수_초에_일어나도_유예는_10초_이상이다() {
        // 12:00:00.900에 회전 → 저장은 초 단위. 내림이면 유예가 12:00:10에 끝나 실제로는 9.1초가 된다
        clock.advance(Duration.ofMillis(900));
        String plain = refreshTokens.create(studentId, null);
        refreshTokens.rotate(plain);
        entityManager.flush();
        entityManager.clear();

        clock.advance(GRACE);

        assertThat(refreshTokens.rotate(plain)).as("회전 뒤 정확히 10초").isInstanceOf(GraceReuse.class);
    }

    @Test
    void 유예가_지난_옛_토큰이면_그_사용자_refresh를_전부_끊는다() {
        String plain = refreshTokens.create(studentId, null);
        String otherDevice = refreshTokens.create(studentId, null);
        String rotated = ((Rotated) refreshTokens.rotate(plain)).newRefresh();

        clock.advance(GRACE.plusMillis(1));

        assertThat(refreshTokens.rotate(plain)).isInstanceOf(Rejected.class);
        assertThat(refreshTokens.rotate(rotated)).as("회전으로 받은 새 토큰도 끊김(유예 없이)").isInstanceOf(Rejected.class);
        assertThat(refreshTokens.rotate(otherDevice)).as("다른 기기도").isInstanceOf(Rejected.class);
    }

    @Test
    void 다른_사용자의_refresh는_끊지_않는다() {
        long other = data.student(academyId, "다른 학생");
        String plain = refreshTokens.create(studentId, null);
        String othersToken = refreshTokens.create(other, null);
        refreshTokens.rotate(plain);
        clock.advance(GRACE.plusSeconds(1));

        refreshTokens.rotate(plain);

        assertThat(refreshTokens.rotate(othersToken)).isInstanceOf(Rotated.class);
    }

    @Test
    void 구십일이_지나면_Rejected() {
        String plain = refreshTokens.create(studentId, null);
        String before = refreshTokens.create(studentId, null);

        clock.advance(Duration.ofDays(90).minusSeconds(1));
        assertThat(refreshTokens.rotate(before)).isInstanceOf(Rotated.class);

        clock.advance(Duration.ofSeconds(1));
        assertThat(refreshTokens.rotate(plain)).isInstanceOf(Rejected.class);
    }

    @Test
    void 모르는_토큰은_Rejected() {
        assertThat(refreshTokens.rotate("no-such-token")).isInstanceOf(Rejected.class);
    }

    @Test
    void 로그아웃한_토큰은_유예_없이_바로_Rejected() {
        String plain = refreshTokens.create(studentId, null);

        refreshTokens.revoke(plain);

        assertThat(refreshTokens.rotate(plain)).isInstanceOf(Rejected.class);
    }

    @Test
    void 로그아웃_직후_DB_초_반올림이_있어도_바로_Rejected() {
        // DATETIME은 소수 초를 반올림한다: 12:00:00.600에 끊으면 12:00:01로 저장돼 0.4초 동안 "만료 전·유예 안"으로 보일 수 있다
        clock.advance(Duration.ofMillis(600));
        String plain = refreshTokens.create(studentId, null);
        refreshTokens.revoke(plain);
        entityManager.flush();
        entityManager.clear();

        clock.advance(Duration.ofMillis(200));

        assertThat(refreshTokens.rotate(plain)).isInstanceOf(Rejected.class);
    }

    @Test
    void 재사용_감지_직후_DB_초_반올림이_있어도_새_토큰은_Rejected() {
        clock.advance(Duration.ofMillis(600));
        String plain = refreshTokens.create(studentId, null);
        String rotated = ((Rotated) refreshTokens.rotate(plain)).newRefresh();
        clock.advance(GRACE.plusSeconds(1));
        refreshTokens.rotate(plain); // 유예 뒤 재사용 → 전부 끊기
        entityManager.flush();
        entityManager.clear();

        clock.advance(Duration.ofMillis(200));

        assertThat(refreshTokens.rotate(rotated)).isInstanceOf(Rejected.class);
    }

    @Test
    void 비활성_사용자의_refresh는_Rejected() {
        String plain = refreshTokens.create(studentId, null);
        jdbc.update("update `user` set status = 'inactive' where id = ?", studentId);

        assertThat(refreshTokens.rotate(plain)).isInstanceOf(Rejected.class);
    }

    @Test
    void 정지된_학원_사용자의_refresh는_Rejected() {
        String plain = refreshTokens.create(studentId, null);
        jdbc.update("update academy set status = 'suspended' where id = ?", academyId);

        assertThat(refreshTokens.rotate(plain)).isInstanceOf(Rejected.class);
    }

    @Test
    void user_agent는_200자로_자른다() {
        refreshTokens.create(studentId, "a".repeat(300));

        assertThat(jdbc.queryForObject(
                        "select char_length(user_agent) from refresh_token where user_id = ?",
                        Integer.class,
                        studentId))
                .isEqualTo(200);
    }
}
