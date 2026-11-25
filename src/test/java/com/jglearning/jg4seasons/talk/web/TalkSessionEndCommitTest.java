package com.jglearning.jg4seasons.talk.web;

import static com.jglearning.jg4seasons.talk.web.TalkTestSupport.create;
import static com.jglearning.jg4seasons.talk.web.TalkTestSupport.features;
import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.jglearning.jg4seasons.IntegrationTest;
import com.jglearning.jg4seasons.MutableClock;
import com.jglearning.jg4seasons.TestData;
import com.jglearning.jg4seasons.auth.AuthCookieSupport;
import com.jglearning.jg4seasons.talk.summary.FakeTalkSummarizer;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/** 같은 세션 종료 동시 2건(끝내기와 시간 종료가 겹침). 실제 커밋이 필요해 @Transactional 없이 돌리고 끝에 지운다 */
@IntegrationTest
class TalkSessionEndCommitTest {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    AuthCookieSupport cookies;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    MutableClock clock;

    @Autowired
    FakeTalkSummarizer summarizer;

    long studentId;
    long academyId;

    @BeforeEach
    void setUp() {
        clock.set(Instant.parse("2026-11-26T03:00:00Z"));
        summarizer.reset();
        TestData data = new TestData(jdbc);
        academyId = data.academy("동시 종료 학원");
        features(jdbc, academyId, true, true);
        long classId = data.clazz(academyId, "L2-B1", "커밋 반", TestData.uniqueAccessCode(), "active");
        studentId = data.student(academyId, "동시 종료 학생");
        data.enroll(classId, studentId);
        data.session(classId, 1, "L2-B1-03", LocalDate.of(2026, 11, 26), "scheduled");
    }

    @AfterEach
    void cleanUp() {
        clock.reset();
        summarizer.reset();
        jdbc.update(
                "delete from talk_turn where session_id in (select id from talk_session where student_id = ?)",
                studentId);
        jdbc.update("delete from talk_session where student_id = ?", studentId);
    }

    @Test
    void 동시_종료_2건은_한_번만_저장하고_요약한다() throws Exception {
        String created = create(
                        mvc,
                        cookies.studentAccess(studentId, academyId),
                        UUID.randomUUID().toString(),
                        "day",
                        "male")
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        long id = ((Number) JsonPath.read(created, "$.id")).longValue();
        String body = TalkSessionEndTest.body(120, "user", TalkSessionEndTest.TURNS, null);

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                Callable<Integer> call = () -> {
                    start.await();
                    return mvc.post()
                            .uri(TalkTestSupport.SESSIONS + "/" + id + "/end")
                            .cookie(cookies.studentAccess(studentId, academyId), AuthCookieSupport.csrf())
                            .header(AuthCookieSupport.CSRF_HEADER, AuthCookieSupport.CSRF_VALUE)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body)
                            .exchange()
                            .getResponse()
                            .getStatus();
                };
                results.add(pool.submit(call));
            }
            start.countDown();
            for (Future<Integer> result : results) {
                assertThat(result.get(30, TimeUnit.SECONDS)).isEqualTo(200);
            }

            assertThat(jdbc.queryForObject("select count(*) from talk_turn where session_id = ?", Integer.class, id))
                    .isEqualTo(4);
            assertThat(summarizer.calls()).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }
}
