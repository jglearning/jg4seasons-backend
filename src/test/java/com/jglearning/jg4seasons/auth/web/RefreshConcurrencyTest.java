package com.jglearning.jg4seasons.auth.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.jglearning.jg4seasons.IntegrationTest;
import com.jglearning.jg4seasons.MutableClock;
import com.jglearning.jg4seasons.TestData;
import com.jglearning.jg4seasons.auth.token.RefreshTokenService;
import com.jglearning.jg4seasons.auth.token.SessionTtl;
import jakarta.servlet.http.Cookie;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * 같은 refresh 쿠키로 동시 2건(탭 둘): 하나만 회전하고 다른 하나는 유예 200. 둘 다 401이 되거나 유효한 refresh가 둘 생기면 안 된다.
 *
 * <p>다른 스레드가 데이터를 봐야 해서 트랜잭션으로 되돌리지 않는다. 넣은 데이터는 끝에 지운다.
 */
@IntegrationTest
class RefreshConcurrencyTest {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    RefreshTokenService refreshTokens;

    @Autowired
    MutableClock clock;

    long academyId;
    long classId;
    long studentId;

    @BeforeEach
    void setUp() {
        clock.reset(); // 앱 시계를 실제 시각에 맞춘다(유효 토큰 판정을 같은 시계로)
        TestData data = new TestData(jdbc);
        academyId = data.academy("동시성 학원");
        classId = data.clazz(academyId, "L2-B1", "A반", TestData.uniqueAccessCode(), "active");
        studentId = data.student(academyId, "학생");
        data.enroll(classId, studentId);
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("delete from refresh_token where user_id = ?", studentId);
        jdbc.update("delete from enrollment where student_id = ?", studentId);
        jdbc.update("delete from `user` where id = ?", studentId);
        jdbc.update("delete from `class` where id = ?", classId);
        jdbc.update("delete from academy where id = ?", academyId);
    }

    @Test
    void 같은_refresh_동시_2건은_하나만_회전한다() throws Exception {
        String refresh = refreshTokens.create(studentId, null, SessionTtl.STUDENT_REFRESH);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<MvcTestResult>> futures = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                go.await();
                return mvc.post()
                        .uri("/api/v1/auth/refresh")
                        .cookie(new Cookie("refresh", refresh))
                        .exchange();
            }));
        }
        ready.await();
        go.countDown();
        List<MvcTestResult> results = new ArrayList<>();
        for (Future<MvcTestResult> future : futures) {
            results.add(future.get(20, TimeUnit.SECONDS));
        }
        pool.shutdown();

        assertThat(results).allSatisfy(result -> assertThat(result).hasStatusOk());
        long rotated = results.stream()
                .filter(result -> result.getResponse().getHeaders(HttpHeaders.SET_COOKIE).stream()
                        .anyMatch(c -> c.startsWith("refresh=")))
                .count();
        assertThat(rotated).as("새 refresh를 받은 응답").isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        "select count(*) from refresh_token where user_id = ? and revoked_at is null"
                                + " and expires_at > ?",
                        Integer.class,
                        studentId,
                        LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC)))
                .as("유효한 refresh")
                .isEqualTo(1);
    }
}
