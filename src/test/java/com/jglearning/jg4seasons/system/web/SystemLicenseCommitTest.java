package com.jglearning.jg4seasons.system.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.jglearning.jg4seasons.IntegrationTest;
import com.jglearning.jg4seasons.TestData;
import com.jglearning.jg4seasons.auth.AuthCookieSupport;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/**
 * 실제로 커밋되는 7.4 동시 PUT(리뷰에서 볼 곳 5, CLAUDE.md §11). 같은 학원·권의 첫 PUT이 동시에 두 번 와도 학원 행 잠금으로 줄을 서 한 줄로 끝나야 한다 — 줄을
 * 세우지 않으면 둘 다 "없음"을 보고 넣어 유일 키 위반(500)이 난다. 학원을 바꿔 여러 번. 넣은 행은 끝에 지운다.
 */
@IntegrationTest
class SystemLicenseCommitTest {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    AuthCookieSupport cookies;

    @Autowired
    JdbcTemplate jdbc;

    TestData data;
    long operatorId;
    List<Long> academyIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        data = new TestData(jdbc);
        operatorId = data.operator("커밋 운영자");
    }

    @AfterEach
    void cleanUp() {
        for (long academyId : academyIds) {
            jdbc.update("delete from audit_log where academy_id = ?", academyId);
            jdbc.update("delete from book_license where academy_id = ?", academyId);
            jdbc.update("delete from academy where id = ?", academyId);
        }
        jdbc.update("delete from `user` where id = ?", operatorId);
    }

    private Callable<Integer> put(long academyId, int seats) {
        return () -> mvc.put()
                .uri("/api/v1/system/academies/" + academyId + "/licenses/L2-B1")
                .cookie(cookies.operatorAccess(operatorId), AuthCookieSupport.csrf())
                .header(AuthCookieSupport.CSRF_HEADER, AuthCookieSupport.CSRF_VALUE)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"startDate\":\"2026-12-01\",\"endDate\":\"2027-02-28\",\"seats\":" + seats + "}")
                .exchange()
                .getResponse()
                .getStatus();
    }

    private static List<Integer> together(Callable<Integer> first, Callable<Integer> second) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Integer>> futures = new ArrayList<>();
            for (Callable<Integer> call : List.of(first, second)) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return call.call();
                }));
            }
            start.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> future : futures) {
                statuses.add(future.get(30, TimeUnit.SECONDS));
            }
            return statuses;
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void 같은_학원_권에_첫_PUT이_동시에_와도_한_줄() throws Exception {
        for (int round = 0; round < 5; round++) {
            long academyId = data.academy("동시 사용권 학원 " + round);
            academyIds.add(academyId);
            data.enforceLicenses(academyId);

            List<Integer> statuses = together(put(academyId, 10), put(academyId, 20));

            String as = "회차 " + round;
            assertThat(statuses).as(as).containsExactly(200, 200);
            assertThat(jdbc.queryForObject(
                            "select count(*) from book_license where academy_id = ?", Integer.class, academyId))
                    .as(as)
                    .isEqualTo(1);
            assertThat(jdbc.queryForObject(
                            "select count(*) from audit_log where academy_id = ? and action = 'LICENSE_SET'",
                            Integer.class,
                            academyId))
                    .as(as + " 감사 2행(처음 + 고침)")
                    .isEqualTo(2);
        }
    }
}
