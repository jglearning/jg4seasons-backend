package com.jglearning.jg4seasons.wordtest.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.jglearning.jg4seasons.IntegrationTest;
import com.jglearning.jg4seasons.MutableClock;
import com.jglearning.jg4seasons.TestData;
import com.jglearning.jg4seasons.auth.AuthCookieSupport;
import com.jglearning.jg4seasons.infra.storage.MemoryPhotoStorage;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
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
 * 같은 clientSubmissionId 동시 제출(두 번 누름·재시도가 겹침): 제출 1건·작업 1건, 두 응답 모두 같은 id. 트랜잭션이 실제로 커밋돼야 하므로 @Transactional 없이 돌리고 끝에
 * 지운다
 */
@IntegrationTest
class WordTestSubmitCommitTest {

    static final String CODE = "L2-B1-01";

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
        String clientId = UUID.randomUUID().toString();
        String key = "homework/" + academyId + "/" + studentId + "/" + UUID.randomUUID() + ".jpg";
        photos.put(key, new byte[] {1}, "image/jpeg");
        String body = "{\"clientSubmissionId\":\"" + clientId + "\",\"lessonCode\":\"" + CODE
                + "\",\"section\":\"B\",\"imageKey\":\"" + key + "\"}";

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<int[]>> results = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            results.add(pool.submit(() -> {
                start.await();
                var result = mvc.post()
                        .uri("/api/v1/word-tests/submissions")
                        .cookie(cookies.studentAccess(studentId, academyId), AuthCookieSupport.csrf())
                        .header(AuthCookieSupport.CSRF_HEADER, AuthCookieSupport.CSRF_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .exchange();
                Number id = (Number)
                        com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.id");
                return new int[] {result.getResponse().getStatus(), id.intValue()};
            }));
        }
        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

        int[] first = results.get(0).get();
        int[] second = results.get(1).get();
        assertThat(List.of(first[0], second[0])).containsExactlyInAnyOrder(202, 200);
        assertThat(first[1]).isEqualTo(second[1]);
        assertThat(jdbc.queryForObject(
                        "select count(*) from word_test_submission where student_id = ?", Integer.class, studentId))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        "select count(*) from grading_job j join word_test_submission s on s.id = j.submission_id"
                                + " where s.student_id = ?",
                        Integer.class,
                        studentId))
                .isEqualTo(1);
    }
}
