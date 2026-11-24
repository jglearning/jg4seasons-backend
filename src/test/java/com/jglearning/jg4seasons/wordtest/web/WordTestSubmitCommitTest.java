package com.jglearning.jg4seasons.wordtest.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.jglearning.jg4seasons.IntegrationTest;
import com.jglearning.jg4seasons.MutableClock;
import com.jglearning.jg4seasons.TestData;
import com.jglearning.jg4seasons.auth.AuthCookieSupport;
import com.jglearning.jg4seasons.infra.storage.MemoryPhotoStorage;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 동시 제출(두 번 누름·탭 둘·재시도가 겹침). 트랜잭션이 실제로 커밋돼야 하므로 @Transactional 없이 돌리고 끝에 지운다. 학생 범위로만 센다(다른 테스트가 남긴 행과 섞이지 않게)
 */
@IntegrationTest
class WordTestSubmitCommitTest {

    static final String CODE = "L2-B1-01";
    static final String URL = "/api/v1/word-tests/submissions";

    @Autowired
    MockMvcTester mvc;

    @Autowired
    AuthCookieSupport cookies;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    MutableClock clock;

    @Autowired
    MemoryPhotoStorage photos;

    @Autowired
    PlatformTransactionManager transactions;

    TestData data;
    long academyId;
    long classId;
    long studentId;
    long lessonId;

    @BeforeEach
    void setUp() {
        clock.set(Instant.parse("2026-11-26T03:00:00Z"));
        data = new TestData(jdbc);
        academyId = data.academy("동시 제출 학원");
        WordTestUploadTest.enableWordTest(jdbc, academyId);
        classId = data.clazz(academyId, "L2-B1", "커밋 반", TestData.uniqueAccessCode(), "active");
        studentId = data.student(academyId, "동시 학생");
        data.enroll(classId, studentId);
        data.session(classId, 1, CODE, LocalDate.of(2026, 11, 26), "scheduled");
        lessonId = data.lessonId(CODE);
        jdbc.update(
                "insert into word_test_item (lesson_id, section, no, prompt, answer) values (?, 'B', 1, '뜻', 'word')",
                lessonId);
    }

    @AfterEach
    void cleanUp() {
        clock.reset();
        photos.clear();
        jdbc.update(
                "delete from grading_job where submission_id in"
                        + " (select id from word_test_submission where student_id = ?)",
                studentId);
        jdbc.update("delete from word_test_submission where student_id = ?", studentId);
        jdbc.update("delete from word_test_item where lesson_id = ?", lessonId);
        jdbc.update("delete from class_session where class_id = ?", classId);
        jdbc.update("delete from enrollment where class_id = ?", classId);
        jdbc.update("delete from `class` where id = ?", classId);
        jdbc.update("delete from `user` where academy_id = ?", academyId);
        jdbc.update("delete from academy where id = ?", academyId);
    }

    @Test
    void 같은_clientSubmissionId_동시_2건은_제출_하나와_같은_id() throws Exception {
        String body = body(UUID.randomUUID().toString(), photo());

        List<int[]> results = together(() -> submit(body), () -> submit(body));

        assertThat(List.of(results.get(0)[0], results.get(1)[0])).containsExactlyInAnyOrder(202, 200);
        assertThat(results.get(0)[1]).isEqualTo(results.get(1)[1]);
        assertThat(submissions()).isEqualTo(1);
        assertThat(jobs()).isEqualTo(1);
    }

    @Test
    void 다른_clientSubmissionId_동시_2건은_둘_다_202이고_시도_번호_1과_2() throws Exception {
        String first = body(UUID.randomUUID().toString(), photo());
        String second = body(UUID.randomUUID().toString(), photo());

        List<int[]> results = together(() -> submit(first), () -> submit(second));

        assertThat(List.of(results.get(0)[0], results.get(1)[0])).containsExactly(202, 202);
        assertThat(jdbc.queryForList(
                        "select attempt_no from word_test_submission where student_id = ?", Integer.class, studentId))
                .containsExactlyInAnyOrder(1, 2);
        assertThat(jobs()).isEqualTo(2);
    }

    @Test
    void 같은_clientSubmissionId가_커밋_전_INSERT와_겹치면_먼저_커밋된_제출을_200으로() throws Exception {
        // 겹침을 확정한다: 다른 트랜잭션이 같은 id 행을 넣고 커밋하지 않은 채 기다리는 동안 요청이 들어온다.
        // 요청의 첫 조회는 그 행을 못 보고 INSERT에서 UNIQUE에 걸린다 → 잠금 다시 읽기로 먼저 커밋된 제출을 돌려줘야 한다
        String clientId = UUID.randomUUID().toString();
        String body = body(clientId, photo());
        CountDownLatch inserted = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        long[] holderId = new long[1];
        ExecutorService pool = Executors.newFixedThreadPool(2);
        Future<?> holder = pool.submit(() -> new TransactionTemplate(transactions).executeWithoutResult(status -> {
            jdbc.update(
                    "insert into word_test_submission (client_submission_id, student_id, lesson_id, section,"
                            + " attempt_no, image_key, status, total, created_at) values (?, ?, ?, 'B', 1, 'k', 'pending', 1, ?)",
                    clientId,
                    studentId,
                    lessonId,
                    LocalDateTime.of(2026, 11, 26, 3, 0));
            holderId[0] = jdbc.queryForObject("select last_insert_id()", Long.class);
            inserted.countDown();
            await(release);
        }));
        await(inserted);
        Future<int[]> request = pool.submit(() -> submit(body));
        // 요청이 첫 조회를 지나 INSERT에 닿을 시간. 더 늦게 닿아도 커밋된 행에 바로 UNIQUE가 걸려 같은 분기를 지난다
        Thread.sleep(1500);
        release.countDown();
        holder.get(30, TimeUnit.SECONDS);
        int[] result = request.get(30, TimeUnit.SECONDS);
        pool.shutdown();

        assertThat(result[0]).isEqualTo(200);
        assertThat((long) result[1]).isEqualTo(holderId[0]);
        assertThat(submissions()).isEqualTo(1);
    }

    private static void await(CountDownLatch latch) {
        try {
            assertThat(latch.await(30, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    /** 두 요청을 한 번에 출발시킨다 */
    private static List<int[]> together(Callable<int[]> a, Callable<int[]> b) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<int[]>> futures = new ArrayList<>();
        for (Callable<int[]> call : List.of(a, b)) {
            futures.add(pool.submit(() -> {
                start.await();
                return call.call();
            }));
        }
        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        return List.of(futures.get(0).get(), futures.get(1).get());
    }

    /** [상태, id(없으면 -1)] */
    private int[] submit(String body) throws Exception {
        var result = mvc.post()
                .uri(URL)
                .cookie(cookies.studentAccess(studentId, academyId), AuthCookieSupport.csrf())
                .header(AuthCookieSupport.CSRF_HEADER, AuthCookieSupport.CSRF_VALUE)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();
        String json = result.getResponse().getContentAsString();
        int status = result.getResponse().getStatus();
        int id = status < 300 ? ((Number) JsonPath.read(json, "$.id")).intValue() : -1;
        return new int[] {status, id};
    }

    private String photo() {
        String key = "homework/" + academyId + "/" + studentId + "/" + UUID.randomUUID() + ".jpg";
        photos.put(key, new byte[] {1}, "image/jpeg");
        return key;
    }

    private static String body(String clientId, String key) {
        return "{\"clientSubmissionId\":\"" + clientId + "\",\"lessonCode\":\"" + CODE
                + "\",\"section\":\"B\",\"imageKey\":\"" + key + "\"}";
    }

    private int submissions() {
        return jdbc.queryForObject(
                "select count(*) from word_test_submission where student_id = ?", Integer.class, studentId);
    }

    private int jobs() {
        return jdbc.queryForObject(
                "select count(*) from grading_job j join word_test_submission s on s.id = j.submission_id"
                        + " where s.student_id = ?",
                Integer.class,
                studentId);
    }
}
