package com.jglearning.jg4seasons.talk.web;

import static com.jglearning.jg4seasons.talk.web.TalkTestSupport.create;
import static com.jglearning.jg4seasons.talk.web.TalkTestSupport.features;
import static org.assertj.core.api.Assertions.assertThat;

import com.jglearning.jg4seasons.IntegrationTest;
import com.jglearning.jg4seasons.MutableClock;
import com.jglearning.jg4seasons.TestData;
import com.jglearning.jg4seasons.auth.AuthCookieSupport;
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
            List<Future<Integer>> results = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                Callable<Integer> call = () -> {
                    start.await();
                    return create(mvc, cookies.studentAccess(studentId, academyId), clientId, "day", "female")
                            .getResponse()
                            .getStatus();
                };
                results.add(pool.submit(call));
            }
            start.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> result : results) {
                statuses.add(result.get(30, TimeUnit.SECONDS));
            }

            assertThat(statuses).containsExactlyInAnyOrder(201, 200);
            assertThat(jdbc.queryForObject(
                            "select count(*) from talk_session where client_session_id = ?", Integer.class, clientId))
                    .isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }
}
