package com.jglearning.jg4seasons.admin.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.jglearning.jg4seasons.IntegrationTest;
import com.jglearning.jg4seasons.TestData;
import com.jglearning.jg4seasons.auth.AuthCookieSupport;
import com.jglearning.jg4seasons.auth.UserRole;
import com.jglearning.jg4seasons.auth.token.RefreshOutcome;
import com.jglearning.jg4seasons.auth.token.RefreshTokenService;
import com.jglearning.jg4seasons.auth.token.SessionTtl;
import jakarta.servlet.http.Cookie;
import java.util.ArrayList;
import java.util.List;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * 실제로 커밋되는 요청에서의 관리자 교사 API. 트랜잭션으로 되돌리는 테스트는 서비스가 테스트 트랜잭션에 합류해 롤백 여부(반 배정 404면 계정도 없어야
 * 함)와 동시 요청을 보지 못한다. 넣은 데이터는 끝에 지운다.
 */
@IntegrationTest
class TeacherAdminCommitTest {

    static final String URL = "/api/v1/admin/teachers";

    @Autowired
    MockMvcTester mvc;

    @Autowired
    AuthCookieSupport cookies;

    @Autowired
    RefreshTokenService refreshTokens;

    @Autowired
    PasswordEncoder encoder;

    @Autowired
    JdbcTemplate jdbc;

    long academyId;
    long otherAcademy;
    long adminId;
    long classA;
    long otherClass;

    @BeforeEach
    void setUp() {
        TestData data = new TestData(jdbc);
        academyId = data.academy("커밋 학원");
        otherAcademy = data.academy("다른 커밋 학원");
        adminId = data.staff(academyId, "관리자", "admin");
        classA = data.clazz(academyId, "L2-B1", "A반", TestData.uniqueAccessCode(), "active");
        otherClass = data.clazz(otherAcademy, "L2-B1", "다른 반", TestData.uniqueAccessCode(), "active");
    }

    @AfterEach
    void cleanUp() {
        for (long academy : new long[] {academyId, otherAcademy}) {
            String users = "(select id from (select id from `user` where academy_id = " + academy + ") u)";
            jdbc.update("delete from class_teacher where user_id in " + users);
            jdbc.update("delete from refresh_token where user_id in " + users);
            jdbc.update("delete from auth_identity where user_id in " + users);
            jdbc.update("delete from `class` where academy_id = ?", academy);
            jdbc.update("delete from `user` where academy_id = ?", academy);
            jdbc.update("delete from academy where id = ?", academy);
        }
    }

    private Cookie[] admin() {
        return new Cookie[] {cookies.access(adminId, UserRole.ADMIN, academyId), AuthCookieSupport.csrf()};
    }

    private MvcTestResult create(String loginId, long... classIds) {
        StringBuilder ids = new StringBuilder();
        for (long id : classIds) {
            ids.append(ids.isEmpty() ? "" : ",").append(id);
        }
        return mvc.post()
                .uri(URL)
                .cookie(admin())
                .header(AuthCookieSupport.CSRF_HEADER, AuthCookieSupport.CSRF_VALUE)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"교사\",\"loginId\":\"" + loginId
                        + "\",\"password\":\"teacher-pass-1\",\"role\":\"teacher\",\"classIds\":[" + ids + "]}")
                .exchange();
    }

    private MvcTestResult patch(long id, String body) {
        return mvc.patch()
                .uri(URL + "/" + id)
                .cookie(admin())
                .header(AuthCookieSupport.CSRF_HEADER, AuthCookieSupport.CSRF_VALUE)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();
    }

    private int staffCount() {
        return jdbc.queryForObject(
                "select count(*) from `user` where academy_id = ? and role in ('teacher', 'admin')",
                Integer.class,
                academyId);
    }

    private long idOf(MvcTestResult result) throws Exception {
        return ((Number) com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.id"))
                .longValue();
    }

    @Test
    void 다른_학원_반이_섞이면_404이고_계정과_아이디가_남지_않는다() {
        int before = staffCount();

        assertThat(create("rollback-teacher", classA, otherClass)).hasStatus(404);

        assertThat(staffCount()).as("계정이 롤백됨").isEqualTo(before);
        assertThat(jdbc.queryForObject(
                        "select count(*) from auth_identity where identifier = 'rollback-teacher'", Integer.class))
                .isZero();
        assertThat(create("rollback-teacher", classA))
                .as("같은 아이디로 다시 만들 수 있다(409 아님)")
                .hasStatus(201);
    }

    @Test
    void PATCH가_404면_상태와_비밀번호와_세션이_그대로다() throws Exception {
        long teacherId = idOf(create("keep-teacher", classA));
        String refresh = refreshTokens.create(teacherId, null, SessionTtl.STAFF_REFRESH);

        MvcTestResult result = patch(
                teacherId, "{\"status\":\"inactive\",\"password\":\"new-pass-99\",\"classIds\":[" + otherClass + "]}");

        assertThat(result).hasStatus(404);
        assertThat(jdbc.queryForObject("select status from `user` where id = ?", String.class, teacherId))
                .isEqualTo("active");
        String hash =
                jdbc.queryForObject("select secret_hash from auth_identity where user_id = ?", String.class, teacherId);
        assertThat(encoder.matches("teacher-pass-1", hash)).as("예전 비밀번호 그대로").isTrue();
        assertThat(refreshTokens.rotate(refresh)).as("refresh 끊기도 롤백").isInstanceOf(RefreshOutcome.Rotated.class);
    }

    @Test
    void 비활성화했다가_다시_활성화해도_예전_refresh는_살아나지_않는다() throws Exception {
        // 비활성 동안 refresh를 쓰지 않으면 회전 쪽의 "비활성 소유자 거부"가 돌지 않는다 → 비활성화 때 직접 끊어야 한다
        long teacherId = idOf(create("come-back", classA));
        String refresh = refreshTokens.create(teacherId, null, SessionTtl.STAFF_REFRESH);

        assertThat(patch(teacherId, "{\"status\":\"inactive\"}")).hasStatusOk();
        assertThat(patch(teacherId, "{\"status\":\"active\"}")).hasStatusOk();

        assertThat(refreshTokens.rotate(refresh)).isInstanceOf(RefreshOutcome.Rejected.class);
    }

    @Test
    void 같은_아이디로_동시에_만들면_하나만_201이고_고아_사용자가_없다() throws Exception {
        int before = staffCount();

        List<Integer> statuses =
                concurrently(2, i -> () -> create("race-teacher").getResponse().getStatus());

        assertThat(statuses).containsExactlyInAnyOrder(201, 409);
        assertThat(staffCount()).as("실패한 쪽의 사용자 행이 남지 않음").isEqualTo(before + 1);
    }

    @Test
    void 같은_교사의_담당_반을_동시에_바꿔도_500이_나지_않는다() throws Exception {
        long teacherId = idOf(create("busy-teacher"));

        List<Integer> statuses = concurrently(
                4,
                i -> () -> patch(teacherId, "{\"classIds\":[" + classA + "]}")
                        .getResponse()
                        .getStatus());

        assertThat(statuses).containsOnly(200);
        assertThat(jdbc.queryForObject(
                        "select count(*) from class_teacher where user_id = ?", Integer.class, teacherId))
                .isEqualTo(1);
    }

    interface Task {
        Callable<Integer> make(int index);
    }

    private static List<Integer> concurrently(int count, Task task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(count);
        CountDownLatch ready = new CountDownLatch(count);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Callable<Integer> call = task.make(i);
            futures.add(pool.submit(() -> {
                ready.countDown();
                go.await();
                return call.call();
            }));
        }
        ready.await();
        go.countDown();
        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> future : futures) {
            statuses.add(future.get(30, TimeUnit.SECONDS));
        }
        pool.shutdown();
        return statuses;
    }
}
