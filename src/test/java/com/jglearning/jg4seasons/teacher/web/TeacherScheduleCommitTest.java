package com.jglearning.jg4seasons.teacher.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.jglearning.jg4seasons.IntegrationTest;
import com.jglearning.jg4seasons.MutableClock;
import com.jglearning.jg4seasons.TestData;
import com.jglearning.jg4seasons.auth.AuthCookieSupport;
import com.jglearning.jg4seasons.auth.UserRole;
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
 * 실제로 커밋되는 4.7 동시 휴강(CLAUDE.md §11, ADR 0030 — 같은 반 편집은 반 행 잠금으로 줄 세움). 같은 반에 휴강 두 건이 동시에 와도 둘 다 반영되는지: 줄을 세우지
 * 않으면 한쪽이 다른 쪽 결과를 덮어쓰거나 행 번호가 겹친다. 반을 바꿔 여러 번. 넣은 행은 끝에 지운다.
 */
@IntegrationTest
class TeacherScheduleCommitTest {

    static final Instant NOON_KST = Instant.parse("2026-11-10T03:00:00Z");

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
    long teacherId;
    List<Long> classIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        clock.set(NOON_KST);
        data = new TestData(jdbc);
        academyId = data.academy("진도표 커밋 학원");
        teacherId = data.staff(academyId, "김교사", "teacher");
    }

    @AfterEach
    void cleanUp() {
        clock.reset();
        for (long classId : classIds) {
            jdbc.update("delete from class_session where class_id = ?", classId);
            jdbc.update("delete from class_teacher where class_id = ?", classId);
            jdbc.update("delete from `class` where id = ?", classId);
        }
        jdbc.update("delete from `user` where academy_id = ?", academyId);
        jdbc.update("delete from academy where id = ?", academyId);
    }

    private Callable<Integer> cancel(long classId, int seq) {
        return () -> mvc.post()
                .uri("/api/v1/teacher/classes/" + classId + "/sessions/" + seq + "/cancel")
                .cookie(cookies.access(teacherId, UserRole.TEACHER, academyId), AuthCookieSupport.csrf())
                .header(AuthCookieSupport.CSRF_HEADER, AuthCookieSupport.CSRF_VALUE)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"shiftFollowing\":true}")
                .exchange()
                .getResponse()
                .getStatus();
    }

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
    void 같은_행을_두_번_눌러도_휴강은_한_번만() throws Exception {
        // 더블 클릭: 둘째 요청은 첫 요청 결과(seq 2 = 이미 휴강)를 보고 그대로 200
        for (int round = 1; round <= 3; round++) {
            long classId = data.clazz(academyId, "L2-B1", "더블 반 " + round, TestData.uniqueAccessCode(), "active");
            classIds.add(classId);
            data.assignTeacher(classId, teacherId);
            data.session(classId, 1, "L2-B1-01", LocalDate.of(2026, 11, 10), "scheduled");
            data.session(classId, 2, "L2-B1-02", LocalDate.of(2026, 11, 12), "scheduled");
            data.session(classId, 3, "L2-B1-03", LocalDate.of(2026, 11, 17), "scheduled");

            List<Integer> statuses = together(cancel(classId, 2), cancel(classId, 2));

            String as = "round " + round;
            assertThat(statuses).as(as).containsExactly(200, 200);
            assertThat(jdbc.queryForObject(
                            "select count(*) from class_session where class_id = ?", Integer.class, classId))
                    .as(as + " 휴강 한 번 → 행 +1")
                    .isEqualTo(4);
            assertThat(jdbc.queryForObject(
                            "select count(*) from class_session where class_id = ? and status = 'cancelled'",
                            Integer.class,
                            classId))
                    .as(as)
                    .isEqualTo(1);
        }
    }

    @Test
    void 같은_반에_휴강_두_건이_동시에_와도_둘_다_반영된다() throws Exception {
        for (int round = 1; round <= 5; round++) {
            long classId = data.clazz(academyId, "L2-B1", "커밋 반 " + round, TestData.uniqueAccessCode(), "active");
            classIds.add(classId);
            data.assignTeacher(classId, teacherId);
            // 수업 요일 화·목: 11-10 · 11-12 · 11-17 · 11-19
            data.session(classId, 1, "L2-B1-01", LocalDate.of(2026, 11, 10), "scheduled");
            data.session(classId, 2, "L2-B1-02", LocalDate.of(2026, 11, 12), "scheduled");
            data.session(classId, 3, "L2-B1-03", LocalDate.of(2026, 11, 17), "scheduled");
            data.session(classId, 4, "L2-B1-04", LocalDate.of(2026, 11, 19), "scheduled");

            List<Integer> statuses = together(cancel(classId, 2), cancel(classId, 4));

            String as = "round " + round;
            assertThat(statuses).as(as).containsExactly(200, 200);
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "select seq, lesson_id, scheduled_date, status from class_session where class_id = ? order by seq",
                    classId);
            assertThat(rows).as(as + " 휴강 2건 → 행 +2").hasSize(6);
            assertThat(rows.stream().map(r -> ((Number) r.get("seq")).intValue()))
                    .as(as)
                    .containsExactly(1, 2, 3, 4, 5, 6);
            assertThat(rows.stream().filter(r -> "cancelled".equals(r.get("status"))))
                    .as(as)
                    .hasSize(2);
            List<Map<String, Object>> held = rows.stream()
                    .filter(r -> !"cancelled".equals(r.get("status")))
                    .toList();
            assertThat(held.stream().map(r -> r.get("lesson_id")).distinct())
                    .as(as + " 회차마다 수업 행 하나")
                    .hasSize(4);
            List<LocalDate> heldDates = held.stream()
                    .map(r -> ((java.sql.Date) r.get("scheduled_date")).toLocalDate())
                    .toList();
            assertThat(heldDates).as(as + " 수업 날짜는 겹치지 않고 오름차순").isSorted().doesNotHaveDuplicates();
        }
    }
}
