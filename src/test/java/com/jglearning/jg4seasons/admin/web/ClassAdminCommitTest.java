package com.jglearning.jg4seasons.admin.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.jglearning.jg4seasons.IntegrationTest;
import com.jglearning.jg4seasons.TestData;
import com.jglearning.jg4seasons.auth.AuthCookieSupport;
import com.jglearning.jg4seasons.auth.UserRole;
import jakarta.servlet.http.Cookie;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
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
 * 실제로 커밋되는 요청에서의 관리자 반 API. 트랜잭션으로 되돌리는 테스트는 동시 요청(행 잠금·교착)과 커밋된 결과를 보지 못한다. 넣은 데이터는 끝에 지운다.
 */
@IntegrationTest
class ClassAdminCommitTest {

    static final String URL = "/api/v1/admin/classes";
    static final int ROUNDS = 10;
    /** 두 경로가 엇갈리는 교착은 확률적이다 — 학원 잠금을 빼면 10회로는 4번 중 1번만 잡혀 늘렸다 */
    static final int CROSS_ROUNDS = 60;

    @Autowired
    MockMvcTester mvc;

    @Autowired
    AuthCookieSupport cookies;

    @Autowired
    JdbcTemplate jdbc;

    long academyId;
    long otherAcademy;
    long adminId;
    long teacherA;
    long teacherB;

    @BeforeEach
    void setUp() {
        TestData data = new TestData(jdbc);
        academyId = data.academy("반 커밋 학원");
        otherAcademy = data.academy("다른 반 커밋 학원");
        adminId = data.staff(academyId, "관리자", "admin");
        teacherA = data.staff(academyId, "김교사", "teacher");
        teacherB = data.staff(academyId, "박교사", "teacher");
    }

    @AfterEach
    void cleanUp() {
        for (long academy : new long[] {academyId, otherAcademy}) {
            String classes = "(select id from (select id from `class` where academy_id = " + academy + ") c)";
            jdbc.update("delete from class_session where class_id in " + classes);
            jdbc.update("delete from class_teacher where class_id in " + classes);
            jdbc.update("delete from audit_log where academy_id = ?", academy);
            jdbc.update("delete from `class` where academy_id = ?", academy);
            jdbc.update("delete from `user` where academy_id = ?", academy);
            jdbc.update("delete from academy where id = ?", academy);
        }
    }

    private Cookie[] admin() {
        return new Cookie[] {cookies.access(adminId, UserRole.ADMIN, academyId), AuthCookieSupport.csrf()};
    }

    private MvcTestResult send(String method, String path, String body) {
        var request = "POST".equals(method) ? mvc.post() : mvc.patch();
        return request.uri(path)
                .cookie(admin())
                .header(AuthCookieSupport.CSRF_HEADER, AuthCookieSupport.CSRF_VALUE)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();
    }

    private long createClass(String name, long... teacherIds) throws Exception {
        StringBuilder ids = new StringBuilder();
        for (long id : teacherIds) {
            ids.append(ids.isEmpty() ? "" : ",").append(id);
        }
        MvcTestResult result = send(
                "POST",
                URL,
                "{\"name\":\"" + name + "\",\"bookCode\":\"L2-B1\",\"startDate\":\"2026-11-09\",\"weekdays\":[1,3],"
                        + "\"teacherIds\":[" + ids + "]}");
        assertThat(result).hasStatus(201);
        return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();
    }

    private List<Long> teachersOf(long classId) {
        return jdbc.queryForList(
                "select user_id from class_teacher where class_id = ? order by user_id", Long.class, classId);
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

    @Test
    void 같은_반_teacherIds_동시_PATCH는_둘_다_200이고_결과는_둘_중_하나() throws Exception {
        long classId = createClass("A반", teacherA);

        for (int round = 0; round < ROUNDS; round++) {
            List<Integer> statuses = together(
                    () -> send("PATCH", URL + "/" + classId, "{\"teacherIds\":[" + teacherA + "," + teacherB + "]}"),
                    () -> send("PATCH", URL + "/" + classId, "{\"teacherIds\":[" + teacherB + "]}"));

            assertThat(statuses).as("round %d", round).containsOnly(200);
            assertThat(teachersOf(classId)).as("round %d", round).isIn(List.of(teacherA, teacherB), List.of(teacherB));
        }
    }

    @Test
    void 교사_기준_PATCH와_반_기준_PATCH가_동시에_와도_500이_없다() throws Exception {
        long classA = createClass("A반", teacherA);
        long classB = createClass("B반", teacherA);

        for (int round = 0; round < CROSS_ROUNDS; round++) {
            long target = round % 2 == 0 ? classA : classB;
            List<Integer> statuses = together(
                    () -> send("PATCH", "/api/v1/admin/teachers/" + teacherA, "{\"classIds\":[" + target + "]}"),
                    () -> send("PATCH", URL + "/" + classA, "{\"teacherIds\":[" + teacherA + "," + teacherB + "]}"));

            assertThat(statuses).as("round %d", round).containsOnly(200);
        }
        assertThat(teachersOf(classA)).isSubsetOf(Set.of(teacherA, teacherB));
    }

    @Test
    void 반_기준이_교사를_새로_넣는_동안_교사_기준이_그_교사를_바꿔도_500이_없다() throws Exception {
        // 둘 다 (A반, 교사A)를 넣는다. 반 기준은 반 행 → (FK) user 행, 교사 기준은 user 행 → (FK) 반 행 — 학원 잠금이 없으면 서로 기다린다
        long classA = createClass("A반");
        long classB = createClass("B반");

        for (int round = 0; round < CROSS_ROUNDS; round++) {
            jdbc.update("delete from class_teacher where class_id in (?, ?)", classA, classB);
            List<Integer> statuses = together(
                    () -> send("PATCH", URL + "/" + classA, "{\"teacherIds\":[" + teacherA + "]}"),
                    () -> send(
                            "PATCH",
                            "/api/v1/admin/teachers/" + teacherA,
                            "{\"classIds\":[" + classA + "," + classB + "]}"));

            assertThat(statuses).as("round %d", round).containsOnly(200);
        }
    }

    @Test
    void 반_상태_변경과_교사_기준_PATCH가_동시에_와도_500이_없다() throws Exception {
        long classA = createClass("A반", teacherA);
        long classB = createClass("B반");

        for (int round = 0; round < CROSS_ROUNDS; round++) {
            String status = round % 2 == 0 ? "finished" : "active";
            long target = round % 2 == 0 ? classA : classB;
            List<Integer> statuses = together(
                    () -> send("PATCH", URL + "/" + classA, "{\"status\":\"" + status + "\"}"),
                    () -> send("PATCH", "/api/v1/admin/teachers/" + teacherB, "{\"classIds\":[" + target + "]}"));

            assertThat(statuses).as("round %d", round).containsOnly(200);
        }
    }

    @Test
    void 교사_확인에서_404면_반도_진도표도_남지_않는다() {
        long otherTeacher = new TestData(jdbc).staff(otherAcademy, "남의 교사", "teacher");

        MvcTestResult result = send(
                "POST",
                URL,
                "{\"name\":\"A반\",\"bookCode\":\"L2-B1\",\"startDate\":\"2026-11-09\",\"weekdays\":[1],"
                        + "\"teacherIds\":[" + teacherA + "," + otherTeacher + "]}");

        assertThat(result).hasStatus(404);
        assertThat(jdbc.queryForObject("select count(*) from `class` where academy_id = ?", Integer.class, academyId))
                .isZero();
        assertThat(jdbc.queryForObject(
                        "select count(*) from class_session s join `class` c on c.id = s.class_id where c.academy_id = ?",
                        Integer.class,
                        academyId))
                .isZero();
    }

    @Test
    void 재발급은_커밋되고_감사도_같이_남는다() throws Exception {
        long classId = createClass("A반");
        String oldCode = jdbc.queryForObject("select access_code from `class` where id = ?", String.class, classId);

        MvcTestResult result = send("POST", URL + "/" + classId + "/access-code/regenerate", "");

        assertThat(result).hasStatusOk();
        assertThat(jdbc.queryForObject("select access_code from `class` where id = ?", String.class, classId))
                .isNotEqualTo(oldCode);
        assertThat(jdbc.queryForObject(
                        "select count(*) from audit_log where academy_id = ? and action = 'CODE_REGENERATE'",
                        Integer.class,
                        academyId))
                .isEqualTo(1);
    }
}
