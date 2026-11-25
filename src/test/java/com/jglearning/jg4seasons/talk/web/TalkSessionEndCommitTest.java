package com.jglearning.jg4seasons.talk.web;

import static com.jglearning.jg4seasons.talk.web.TalkTestSupport.create;
import static com.jglearning.jg4seasons.talk.web.TalkTestSupport.features;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jayway.jsonpath.JsonPath;
import com.jglearning.jg4seasons.IntegrationTest;
import com.jglearning.jg4seasons.MutableClock;
import com.jglearning.jg4seasons.TestData;
import com.jglearning.jg4seasons.auth.AuthCookieSupport;
import com.jglearning.jg4seasons.talk.TalkEndReason;
import com.jglearning.jg4seasons.talk.TalkRole;
import com.jglearning.jg4seasons.talk.TalkTranscript;
import com.jglearning.jg4seasons.talk.persistence.TalkSessionStore;
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
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

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

    @Autowired
    TalkSessionStore store;

    @Autowired
    PlatformTransactionManager transactions;

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
                        "day")
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

    @Test
    void 요약하는_동안_종료는_이미_커밋돼_있고_다른_요청이_막히지_않는다() throws Exception {
        // 리뷰 5: 요약(최대 20초)을 트랜잭션 안으로 옮기면 행 잠금·연결을 쥐고, 조회는 created, 다른 종료는 막힌다
        long id = newSession();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        summarizer.blockNext(entered, release);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> first = pool.submit(() -> endStatus(id, "user"));
            assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();

            assertThat(jdbc.queryForObject("select status from talk_session where id = ?", String.class, id))
                    .isEqualTo("ended");
            assertThat(jdbc.queryForObject("select count(*) from talk_turn where session_id = ?", Integer.class, id))
                    .isEqualTo(4);
            String during = mvc.get()
                    .uri(TalkTestSupport.SESSIONS + "/" + id)
                    .cookie(cookies.studentAccess(studentId, academyId))
                    .exchange()
                    .getResponse()
                    .getContentAsString(StandardCharsets.UTF_8);
            assertThat((String) JsonPath.read(during, "$.status")).isEqualTo("ended");
            assertThat((Object) JsonPath.read(during, "$.summary")).isNull();
            Future<Integer> second = pool.submit(() -> endStatus(id, "timeout"));
            assertThat(second.get(5, TimeUnit.SECONDS)).isEqualTo(200);

            release.countDown();
            assertThat(first.get(30, TimeUnit.SECONDS)).isEqualTo(200);
            assertThat(jdbc.queryForObject("select end_reason from talk_session where id = ?", String.class, id))
                    .isEqualTo("user");
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    void 잠금을_쥔_동안_온_종료는_기다렸다가_저장된_결과를_받는다() throws Exception {
        // 리뷰 5: 순서에 기대지 않고 잠금(for update)을 확정적으로 — 잠금이 없으면 두 번째가 글자 기록을 또 넣다 중복 키 500
        long id = newSession();
        CountDownLatch lockHeld = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> holder = pool.submit(() -> new TransactionTemplate(transactions).executeWithoutResult(status -> {
                store.lockOwned(id, studentId).orElseThrow();
                TalkTranscript.Trimmed trimmed = TalkTranscript.trim(
                        List.of(
                                new TalkTranscript.RawTurn(TalkRole.CHARACTER, "Hi!"),
                                new TalkTranscript.RawTurn(TalkRole.STUDENT, "Hello."),
                                new TalkTranscript.RawTurn(TalkRole.CHARACTER, "Bye!"),
                                new TalkTranscript.RawTurn(TalkRole.STUDENT, "Bye.")),
                        60,
                        180);
                store.markEnded(id, trimmed, TalkEndReason.USER, null, Instant.parse("2026-11-26T03:01:00Z"));
                store.insertTurns(id, trimmed.turns());
                lockHeld.countDown();
                try {
                    release.await(30, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }));
            assertThat(lockHeld.await(10, TimeUnit.SECONDS)).isTrue();
            Future<Integer> waiting = pool.submit(() -> endStatus(id, "timeout"));
            assertThatThrownBy(() -> waiting.get(500, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);

            release.countDown();
            holder.get(30, TimeUnit.SECONDS);
            assertThat(waiting.get(30, TimeUnit.SECONDS)).isEqualTo(200);
            assertThat(jdbc.queryForObject("select count(*) from talk_turn where session_id = ?", Integer.class, id))
                    .isEqualTo(4);
            assertThat(summarizer.calls()).isZero();
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    private long newSession() throws Exception {
        String created = create(
                        mvc,
                        cookies.studentAccess(studentId, academyId),
                        UUID.randomUUID().toString(),
                        "day")
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        return ((Number) JsonPath.read(created, "$.id")).longValue();
    }

    private int endStatus(long id, String reason) {
        return mvc.post()
                .uri(TalkTestSupport.SESSIONS + "/" + id + "/end")
                .cookie(cookies.studentAccess(studentId, academyId), AuthCookieSupport.csrf())
                .header(AuthCookieSupport.CSRF_HEADER, AuthCookieSupport.CSRF_VALUE)
                .contentType(MediaType.APPLICATION_JSON)
                .content(TalkSessionEndTest.body(120, reason, TalkSessionEndTest.TURNS, null))
                .exchange()
                .getResponse()
                .getStatus();
    }
}
