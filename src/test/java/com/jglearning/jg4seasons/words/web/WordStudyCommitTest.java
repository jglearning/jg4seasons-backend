package com.jglearning.jg4seasons.words.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.jglearning.jg4seasons.IntegrationTest;
import com.jglearning.jg4seasons.MutableClock;
import com.jglearning.jg4seasons.TestData;
import com.jglearning.jg4seasons.auth.AuthCookieSupport;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
 * 실제로 커밋되는 2.5 동시 저장(CLAUDE.md §11). 행이 없는 상태에서 두 요청이 같은 (학생, 회차) 행을 동시에 만들 때 둘 다 성공하고 큰 값이 남는지. 회차는 다른 테스트가
 * 쓰지 않는 L2-B1-21(단어 10개). 넣은 행은 끝에 지운다.
 */
@IntegrationTest
class WordStudyCommitTest {

    static final String CODE = "L2-B1-21";
    static final Instant NOON = Instant.parse("2026-11-10T03:00:00Z");

    @Autowired
    MockMvcTester mvc;

    @Autowired
    AuthCookieSupport cookies;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    MutableClock clock;

    TestData data;
    long academyId;
    long classId;

    @BeforeEach
    void setUp() {
        clock.set(NOON);
        data = new TestData(jdbc);
        academyId = data.academy("단어 커밋 학원");
        classId = data.clazz(academyId, "L2-B1", "커밋 반", TestData.uniqueAccessCode(), "active");
        data.session(classId, 21, CODE, LocalDate.of(2026, 11, 10), "scheduled");
        for (int i = 1; i <= 10; i++) {
            data.word(CODE, i, "made-up" + i, "book", "n", false);
        }
    }

    @AfterEach
    void cleanUp() {
        clock.reset();
        String students = "(select id from (select id from `user` where academy_id = " + academyId + ") u)";
        jdbc.update("delete from word_study where student_id in " + students);
        jdbc.update("delete from word where lesson_id = (select id from lesson where code = ?)", CODE);
        jdbc.update("delete from class_session where class_id = ?", classId);
        jdbc.update("delete from enrollment where class_id = ?", classId);
        jdbc.update("delete from `class` where id = ?", classId);
        jdbc.update("delete from `user` where academy_id = ?", academyId);
        jdbc.update("delete from academy where id = ?", academyId);
    }

    private Callable<Integer> save(long studentId, String body) {
        return () -> mvc.put()
                .uri("/api/v1/student/lessons/" + CODE + "/word-study")
                .cookie(cookies.studentAccess(studentId, academyId), AuthCookieSupport.csrf())
                .header(AuthCookieSupport.CSRF_HEADER, AuthCookieSupport.CSRF_VALUE)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange()
                .getResponse()
                .getStatus();
    }

    /** 두 요청을 동시에 출발시켜 상태 코드를 돌려준다 */
    private List<Integer> together(Callable<Integer> first, Callable<Integer> second) throws Exception {
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
    void 동시에_처음_저장해도_둘_다_성공하고_큰_값이_남는다() throws Exception {
        // 한 번으로는 경합이 안 날 수 있어 학생을 바꿔 여러 번
        for (int round = 1; round <= 10; round++) {
            long studentId = data.student(academyId, "학생" + round);
            data.enroll(classId, studentId);

            List<Integer> statuses = together(
                    save(studentId, "{\"memKnown\":3,\"cardsViewed\":10}"),
                    save(studentId, "{\"memKnown\":7,\"recallCorrect\":2}"));

            assertThat(statuses).as("round " + round).containsExactly(200, 200);
            Map<String, Object> row = jdbc.queryForMap(
                    "select mem_known, cards_viewed, recall_best, all_viewed_at from word_study where student_id = ?",
                    studentId);
            assertThat(row.get("mem_known")).as("round " + round).isEqualTo(7);
            assertThat(row.get("cards_viewed")).as("round " + round).isEqualTo(10);
            assertThat(row.get("recall_best")).as("round " + round).isEqualTo(2);
            assertThat(row.get("all_viewed_at")).as("round " + round).isNotNull();
        }
    }
}
