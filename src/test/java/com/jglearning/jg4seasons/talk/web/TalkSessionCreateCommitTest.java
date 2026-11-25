package com.jglearning.jg4seasons.talk.web;

import static com.jglearning.jg4seasons.talk.web.TalkTestSupport.create;
import static com.jglearning.jg4seasons.talk.web.TalkTestSupport.features;
import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.jglearning.jg4seasons.IntegrationTest;
import com.jglearning.jg4seasons.MutableClock;
import com.jglearning.jg4seasons.TestData;
import com.jglearning.jg4seasons.auth.AuthCookieSupport;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** 같은 clientSessionId 동시 2건(두 번 누름). 실제 커밋이 필요해 @Transactional 없이 돌리고 끝에 지운다 */
@IntegrationTest
class TalkSessionCreateCommitTest {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    AuthCookieSupport cookies;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    MutableClock clock;

    long studentId;
    long academyId;

    @BeforeEach
    void setUp() {
        clock.set(Instant.parse("2026-11-26T03:00:00Z"));
        TestData data = new TestData(jdbc);
        academyId = data.academy("동시 대화 학원");
        features(jdbc, academyId, true, true);
        long classId = data.clazz(academyId, "L2-B1", "커밋 반", TestData.uniqueAccessCode(), "active");
        studentId = data.student(academyId, "동시 학생");
        data.enroll(classId, studentId);
        data.session(classId, 1, "L2-B1-03", LocalDate.of(2026, 11, 26), "scheduled");
    }

    @AfterEach
    void cleanUp() {
        clock.reset();
        jdbc.update("delete from talk_session where student_id = ?", studentId);
    }

    @Test
    void 같은_clientSessionId_동시_2건은_하나만_만든다() throws Exception {
        String clientId = UUID.randomUUID().toString();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<MvcTestResult>> results = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                Callable<MvcTestResult> call = () -> {
                    start.await();
                    return create(mvc, cookies.studentAccess(studentId, academyId), clientId, "day", "female");
                };
                results.add(pool.submit(call));
            }
            start.countDown();
            List<Integer> statuses = new ArrayList<>();
            List<Number> ids = new ArrayList<>();
            for (Future<MvcTestResult> future : results) {
                MvcTestResult result = future.get(30, TimeUnit.SECONDS);
                statuses.add(result.getResponse().getStatus());
                String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
                ids.add(JsonPath.read(body, "$.id"));
                // 진 쪽도 이긴 세션에 자기가 받은 키를 붙여 돌려준다(리뷰 7)
                assertThat((String) JsonPath.read(body, "$.realtime.clientSecret"))
                        .startsWith("ek_fake_");
            }

            assertThat(statuses).containsExactlyInAnyOrder(201, 200);
            assertThat(jdbc.queryForObject(
                            "select count(*) from talk_session where client_session_id = ?", Integer.class, clientId))
                    .isEqualTo(1);
            assertThat(ids.get(0).longValue()).isEqualTo(ids.get(1).longValue());
        } finally {
            pool.shutdownNow();
        }
    }
}
