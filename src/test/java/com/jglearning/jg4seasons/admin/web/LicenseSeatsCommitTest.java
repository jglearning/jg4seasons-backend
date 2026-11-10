package com.jglearning.jg4seasons.admin.web;

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
 * 실제로 커밋되는 요청에서의 학생 수 한도(ADR 0031 결정 5). 동시 등록은 학원 잠금(lockForAdminWrite)이 줄 세워 한도를 지키는지, 409는 학생 계정까지 되돌리는지
 * 본다. 넣은 데이터는 끝에 지운다.
 */
@IntegrationTest
class LicenseSeatsCommitTest {

    static final Instant NOON_KST = Instant.parse("2026-11-10T03:00:00Z");
    static final LocalDate TODAY = LocalDate.of(2026, 11, 10);
    static final int ROUNDS = 5;

    @Autowired
    MockMvcTester mvc;

    @Autowired
    AuthCookieSupport cookies;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    MutableClock clock;

    long academyId;
    long adminId;
    long classId;

    @BeforeEach
    void setUp() {
        clock.set(NOON_KST);
        TestData data = new TestData(jdbc);
        academyId = data.academy("학생 수 커밋 학원");
        data.enforceLicenses(academyId);
        adminId = data.staff(academyId, "관리자", "admin");
        classId = data.clazz(academyId, "L2-B1", "A반", TestData.uniqueAccessCode(), "active");
        data.license(academyId, "L2-B1", TODAY.minusDays(9), TODAY.plusDays(60), 1);
    }

    @AfterEach
    void cleanUp() {
        clock.reset();
        String users = "(select id from (select id from `user` where academy_id = " + academyId + ") u)";
        jdbc.update("delete from enrollment where class_id = ?", classId);
        jdbc.update("delete from auth_identity where user_id in " + users);
        jdbc.update("delete from audit_log where academy_id = ?", academyId);
        jdbc.update("delete from book_license where academy_id = ?", academyId);
        jdbc.update("delete from `class` where academy_id = ?", academyId);
        jdbc.update("delete from `user` where academy_id = ?", academyId);
        jdbc.update("delete from academy where id = ?", academyId);
    }

    private MvcTestResult register(String... names) {
        StringBuilder students = new StringBuilder();
        for (String name : names) {
            students.append(students.isEmpty() ? "" : ",")
                    .append("{\"name\":\"")
                    .append(name)
                    .append("\"}");
        }
        return mvc.post()
                .uri("/api/v1/admin/classes/" + classId + "/students")
                .cookie(cookies.access(adminId, UserRole.ADMIN, academyId), AuthCookieSupport.csrf())
                .header(AuthCookieSupport.CSRF_HEADER, AuthCookieSupport.CSRF_VALUE)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"students\":[" + students + "]}")
                .exchange();
    }

    private long count(String sql, Object... args) {
        Long n = jdbc.queryForObject(sql, Long.class, args);
        return n == null ? 0 : n;
    }

    private long members() {
        return count("select count(*) from enrollment where class_id = ? and left_at is null", classId);
    }

    private long students() {
        return count("select count(*) from `user` where academy_id = ? and role = 'student'", academyId);
    }

    @Test
    void 한도를_넘는_묶음_등록은_409이고_학생_계정도_만들지_않는다() {
        MvcTestResult result = register("김하나", "김둘");

        assertThat(result).hasStatus(409);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("LICENSE_SEATS_FULL");
        assertThat(members()).isZero();
        assertThat(students()).isZero();
    }

    @Test
    void 남은_한_자리에_동시_등록_두_건이면_하나만_된다() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            // 매 라운드 남은 자리를 하나로
            jdbc.update("update book_license set seats = ? where academy_id = ?", members() + 1, academyId);
            long before = members();
            int r = round;

            List<Integer> statuses = together(() -> register("동시 " + r + "a"), () -> register("동시 " + r + "b"));

            assertThat(statuses).as("round %d", round).containsExactlyInAnyOrder(201, 409);
            assertThat(members()).as("round %d", round).isEqualTo(before + 1);
        }
    }

    /** 두 요청을 동시에 출발시켜 상태 코드를 돌려준다 */
    private List<Integer> together(
            java.util.concurrent.Callable<MvcTestResult> first, java.util.concurrent.Callable<MvcTestResult> second)
            throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Integer>> futures = new ArrayList<>();
            for (var call : List.of(first, second)) {
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
}
