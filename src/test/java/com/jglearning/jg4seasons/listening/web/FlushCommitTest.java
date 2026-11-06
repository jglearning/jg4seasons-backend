package com.jglearning.jg4seasons.listening.web;

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
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * 실제로 커밋되는 flush의 동시성(CLAUDE.md §11). 롤백 테스트는 행 잠금·교착·스냅샷을 보지 못한다. 회차는 다른 테스트가 쓰지 않는 L2-B1-20(지문 2, 1.0만,
 * 6500ms). 넣은 행은 끝에 지운다(테스트 데이터 — 원본 삭제 금지 규칙과 무관).
 */
@IntegrationTest
class FlushCommitTest {

    static final String URL = "/api/v1/listening/flush";
    static final String CODE = "L2-B1-20";
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
    Map<String, Long> tracks;

    @BeforeEach
    void setUp() {
        clock.set(NOON);
        data = new TestData(jdbc);
        academyId = data.academy("flush 커밋 학원");
        classId = data.clazz(academyId, "L2-B1", "커밋 반", TestData.uniqueAccessCode(), "active");
        data.session(classId, 20, CODE, LocalDate.of(2026, 11, 10), "scheduled");
        tracks = data.lessonContent(CODE, 2, 2, List.of("1.0"));
    }

    @AfterEach
    void cleanUp() {
        clock.reset();
        String students = "(select id from (select id from `user` where academy_id = " + academyId + ") u)";
        String trackIds = "(select id from (select id from track where lesson_id = "
                + "(select id from lesson where code = '" + CODE + "')) t)";
        jdbc.update("delete from listening_segment where listening_session_id in"
                + " (select id from (select id from listening_session where student_id in " + students + ") s)");
        jdbc.update("delete from listening_session where student_id in " + students);
        jdbc.update("delete from track_progress where student_id in " + students);
        jdbc.update("delete from lesson_progress where student_id in " + students);
        jdbc.update("delete from sentence_timing where track_id in " + trackIds);
        jdbc.update("delete from track where lesson_id = (select id from lesson where code = ?)", CODE);
        jdbc.update("delete from sentence where passage_id in (select id from (select p.id from passage p"
                + " join lesson l on l.id = p.lesson_id where l.code = '" + CODE + "') p)");
        jdbc.update("delete from passage where lesson_id = (select id from lesson where code = ?)", CODE);
        jdbc.update("delete from class_session where class_id = ?", classId);
        jdbc.update("delete from enrollment where class_id = ?", classId);
        jdbc.update("delete from `class` where id = ?", classId);
        jdbc.update("delete from `user` where academy_id = ?", academyId);
        jdbc.update("delete from academy where id = ?", academyId);
    }

    private long newStudent(int round) {
        long studentId = data.student(academyId, "학생" + round);
        data.enroll(classId, studentId);
        return studentId;
    }

    private Callable<MvcTestResult> flush(long studentId, String sessionId, long trackId, int start, int end) {
        String json = "{\"clientSessionId\":\"" + sessionId + "\",\"trackId\":" + trackId
                + ",\"startedAt\":\"2026-11-10T02:59:00Z\",\"segments\":[{\"startMs\":" + start + ",\"endMs\":" + end
                + ",\"recordedAt\":\"2026-11-10T02:59:59Z\"}]}";
        return () -> mvc.post()
                .uri(URL)
                .contentType(MediaType.APPLICATION_JSON)
                .cookie(cookies.studentAccess(studentId, academyId), AuthCookieSupport.csrf())
                .header(AuthCookieSupport.CSRF_HEADER, AuthCookieSupport.CSRF_VALUE)
                .content(json)
                .exchange();
    }

    /** 두 요청을 동시에 출발시켜 상태 코드를 돌려준다 */
    private List<Integer> together(Callable<MvcTestResult> first, Callable<MvcTestResult> second) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Integer>> futures = new ArrayList<>();
            for (Callable<MvcTestResult> call : List.of(first, second)) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return call.call().getResponse().getStatus();
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

    private Map<String, Object> trackRow(long studentId, long trackId) {
        return jdbc.queryForMap(
                "select merged_ranges, covered_ms, session_count, completed_at from track_progress"
                        + " where student_id = ? and track_id = ?",
                studentId,
                trackId);
    }

    @Test
    void 같은_트랙_동시_flush는_두_구간을_모두_합친다() throws Exception {
        long one = tracks.get("p1:1.0");
        for (int round = 0; round < 20; round++) {
            long studentId = newStudent(round);

            List<Integer> statuses = together(
                    flush(studentId, UUID.randomUUID().toString(), one, 0, 3000),
                    flush(studentId, UUID.randomUUID().toString(), one, 3000, 6500));

            assertThat(statuses).as("round %d", round).containsOnly(200);
            assertThat(trackRow(studentId, one))
                    .as("round %d", round)
                    .containsEntry("merged_ranges", "[[0, 6500]]")
                    .containsEntry("covered_ms", 6500)
                    .containsEntry("session_count", 2);
            assertThat(trackRow(studentId, one).get("completed_at"))
                    .as("round %d", round)
                    .isNotNull();
        }
    }

    @Test
    void 서로_다른_지문을_동시에_완료하면_완료_지문이_2() throws Exception {
        for (int round = 0; round < 30; round++) {
            long studentId = newStudent(round);

            List<Integer> statuses = together(
                    flush(studentId, UUID.randomUUID().toString(), tracks.get("p1:1.0"), 0, 6500),
                    flush(studentId, UUID.randomUUID().toString(), tracks.get("p2:1.0"), 0, 6500));

            assertThat(statuses).as("round %d", round).containsOnly(200);
            assertThat(jdbc.queryForMap(
                            "select status, passages_done, required_done from lesson_progress"
                                    + " where student_id = ? and lesson_id = (select id from lesson where code = ?)",
                            studentId,
                            CODE))
                    .as("round %d", round)
                    .containsEntry("status", "completed")
                    .containsEntry("passages_done", 2)
                    .containsEntry("required_done", 2);
        }
    }

    /** 집계 행이 이미 있으면 INSERT IGNORE가 줄을 세우지 않는다 — 회차 행 FOR UPDATE만이 막는다 */
    @Test
    void 진행_중인_회차에서_두_지문을_동시에_완료해도_완료_지문이_2() throws Exception {
        long p1 = tracks.get("p1:1.0");
        long p2 = tracks.get("p2:1.0");
        for (int round = 0; round < 30; round++) {
            long studentId = newStudent(round);
            assertThat(flush(studentId, UUID.randomUUID().toString(), p1, 0, 100)
                            .call()
                            .getResponse()
                            .getStatus())
                    .isEqualTo(200);
            assertThat(flush(studentId, UUID.randomUUID().toString(), p2, 0, 100)
                            .call()
                            .getResponse()
                            .getStatus())
                    .isEqualTo(200);

            List<Integer> statuses = together(
                    flush(studentId, UUID.randomUUID().toString(), p1, 0, 6500),
                    flush(studentId, UUID.randomUUID().toString(), p2, 0, 6500));

            assertThat(statuses).as("round %d", round).containsOnly(200);
            assertThat(jdbc.queryForMap(
                            "select status, passages_done from lesson_progress"
                                    + " where student_id = ? and lesson_id = (select id from lesson where code = ?)",
                            studentId,
                            CODE))
                    .as("round %d", round)
                    .containsEntry("status", "completed")
                    .containsEntry("passages_done", 2);
        }
    }

    @Test
    void 같은_세션의_첫_flush_동시_2건은_세션_하나() throws Exception {
        long one = tracks.get("p1:1.0");
        for (int round = 0; round < 20; round++) {
            long studentId = newStudent(round);
            String session = UUID.randomUUID().toString();

            List<Integer> statuses =
                    together(flush(studentId, session, one, 0, 1000), flush(studentId, session, one, 1000, 2000));

            assertThat(statuses).as("round %d", round).containsOnly(200);
            assertThat(jdbc.queryForObject(
                            "select count(*) from listening_session where client_session_id = ?",
                            Integer.class,
                            session))
                    .isEqualTo(1);
            assertThat(trackRow(studentId, one))
                    .as("round %d", round)
                    .containsEntry("merged_ranges", "[[0, 2000]]")
                    .containsEntry("session_count", 1);
        }
    }

    @Test
    void 진행_중인_트랙에_같은_트랙_동시_flush도_두_구간을_합친다() throws Exception {
        long one = tracks.get("p1:1.0");
        for (int round = 0; round < 20; round++) {
            long studentId = newStudent(round);
            assertThat(flush(studentId, UUID.randomUUID().toString(), one, 0, 100)
                            .call()
                            .getResponse()
                            .getStatus())
                    .isEqualTo(200);

            List<Integer> statuses = together(
                    flush(studentId, UUID.randomUUID().toString(), one, 100, 3000),
                    flush(studentId, UUID.randomUUID().toString(), one, 3000, 6500));

            assertThat(statuses).as("round %d", round).containsOnly(200);
            assertThat(trackRow(studentId, one))
                    .as("round %d", round)
                    .containsEntry("merged_ranges", "[[0, 6500]]")
                    .containsEntry("session_count", 3);
        }
    }

    @Test
    void 이미_있는_세션의_동시_flush는_세션_하나() throws Exception {
        long one = tracks.get("p1:1.0");
        for (int round = 0; round < 20; round++) {
            long studentId = newStudent(round);
            String session = UUID.randomUUID().toString();
            assertThat(flush(studentId, session, one, 0, 100)
                            .call()
                            .getResponse()
                            .getStatus())
                    .isEqualTo(200);

            List<Integer> statuses =
                    together(flush(studentId, session, one, 100, 1000), flush(studentId, session, one, 1000, 2000));

            assertThat(statuses).as("round %d", round).containsOnly(200);
            assertThat(trackRow(studentId, one))
                    .as("round %d", round)
                    .containsEntry("merged_ranges", "[[0, 2000]]")
                    .containsEntry("session_count", 1);
        }
    }
}
