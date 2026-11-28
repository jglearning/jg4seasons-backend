package com.jglearning.jg4seasons.system.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.jglearning.jg4seasons.IntegrationTest;
import com.jglearning.jg4seasons.MutableClock;
import com.jglearning.jg4seasons.TestData;
import com.jglearning.jg4seasons.auth.AuthCookieSupport;
import com.jglearning.jg4seasons.auth.UserRole;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.annotation.Transactional;

/** 운영자 학생별 프리토킹 켜기·끄기(ADR 0040, O-04). 재원생만, 반 이름 → 이름 순, 반 없는 학생은 맨 뒤. PUT은 하나라도 틀리면 전체 거부 */
@IntegrationTest
@Transactional
class SystemTalkStudentsTest {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    AuthCookieSupport cookies;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    MutableClock clock;

    TestData data;
    long operatorId;
    long academyId;
    long classA;
    long classB;
    long ga;
    long na;
    long da;
    long ra;

    @BeforeEach
    void setUp() {
        clock.set(Instant.parse("2026-11-30T03:00:00Z"));
        data = new TestData(jdbc);
        operatorId = data.operator("운영자");
        academyId = data.academy("프리토킹 학원");
        classA = data.clazz(academyId, "L2-B1", "A반", TestData.uniqueAccessCode(), "active");
        classB = data.clazz(academyId, "L2-B1", "B반", TestData.uniqueAccessCode(), "active");
        da = data.student(academyId, "다");
        ga = data.student(academyId, "가");
        na = data.student(academyId, "나");
        ra = data.student(academyId, "라");
        data.enroll(classB, da);
        data.enroll(classB, ga);
        data.enroll(classA, na);
        data.enroll(classA, ra);
        data.leave(classA, ra); // 반 이동 중(반 없음)
    }

    @AfterEach
    void resetClock() {
        clock.reset();
    }

    @Test
    void 목록은_재원생만_반_이름_이름_순이고_반_없는_학생은_class_null로_맨_뒤() {
        long out = data.student(academyId, "마");
        jdbc.update("update `user` set status = 'inactive' where id = ?", out);

        MvcTestResult result = list(operator(), academyId);

        assertThat(result).hasStatusOk();
        assertThat(result)
                .bodyJson()
                .extractingPath("$.items[*].id")
                .asArray()
                .containsExactly((int) na, (int) ga, (int) da, (int) ra);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.items[0]")
                .isEqualTo(Map.of(
                        "id",
                        (int) na,
                        "name",
                        "나",
                        "class",
                        Map.of("id", (int) classA, "name", "A반"),
                        "talkEnabled",
                        false));
        assertThat(result).bodyJson().extractingPath("$.items[3].class").isNull();
    }

    @Test
    void PUT으로_여러_명을_켜면_목록과_학생_설정_조회에_반영되고_감사_기록_1건() {
        // 요청 전에 켠다(같은 트랜잭션의 JPA가 학원 행을 먼저 읽어 두면 JDBC 변경이 가려진다)
        jdbc.update(
                "update academy set settings = ? where id = ?",
                "{\"timezone\":\"Asia/Seoul\",\"features\":{\"talk\":true,\"talkEntitled\":true}}",
                academyId);
        MvcTestResult result = put(operator(), academyId, "{\"studentIds\":[" + da + "," + na + "],\"enabled\":true}");

        assertThat(result).hasStatusOk();
        // 요청한 학생만, 목록과 같은 순서
        assertThat(result).bodyJson().extractingPath("$.items[*].id").asArray().containsExactly((int) na, (int) da);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.items[*].talkEnabled")
                .asArray()
                .containsExactly(true, true);
        assertThat(list(operator(), academyId))
                .bodyJson()
                .extractingPath("$.items[*].talkEnabled")
                .asArray()
                .containsExactly(true, false, true, false);
        assertThat(jdbc.queryForList(
                        "select detail from audit_log where academy_id = ? and action = 'TALK_ACCESS_SET'",
                        String.class,
                        academyId))
                .singleElement()
                .satisfies(
                        detail -> assertThat(detail).contains("\"students\": 2").contains("\"enabled\": true"));

        assertThat(mvc.get().uri("/api/v1/talk/config").cookie(cookies.studentAccess(na, academyId)))
                .hasStatusOk();
        assertThat(mvc.get().uri("/api/v1/talk/config").cookie(cookies.studentAccess(ga, academyId)))
                .hasStatus(404);
    }

    @Test
    void 같은_값으로_다시_PUT해도_그대로() {
        String body = "{\"studentIds\":[" + ga + "," + ga + "],\"enabled\":true}";
        assertThat(put(operator(), academyId, body)).hasStatusOk();

        MvcTestResult again = put(operator(), academyId, body);

        assertThat(again).hasStatusOk();
        assertThat(again).bodyJson().extractingPath("$.items[*].id").asArray().containsExactly((int) ga);
        assertThat(count("select count(*) from talk_student_access where student_id = ?", ga))
                .isEqualTo(1);
    }

    @Test
    void 다른_학원_학생이_섞이면_404이고_아무도_바뀌지_않는다() {
        long otherAcademy = data.academy("다른 학원");
        long stranger = data.student(otherAcademy, "남");

        MvcTestResult result =
                put(operator(), academyId, "{\"studentIds\":[" + ga + "," + stranger + "],\"enabled\":true}");

        assertThat(result).hasStatus(404);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("NOT_FOUND");
        assertThat(count("select count(*) from talk_student_access where student_id in (?, ?)", ga, stranger))
                .isZero();
        assertThat(count(
                        "select count(*) from audit_log where action = 'TALK_ACCESS_SET' and academy_id = ?",
                        academyId))
                .isZero();
    }

    @Test
    void 퇴원생_id면_404() {
        jdbc.update("update `user` set status = 'inactive' where id = ?", da);

        assertThat(put(operator(), academyId, "{\"studentIds\":[" + da + "],\"enabled\":true}"))
                .hasStatus(404);
    }

    @Test
    void studentIds가_비었거나_501개면_400_VALIDATION() {
        String many = LongStream.rangeClosed(1, 501).mapToObj(Long::toString).collect(Collectors.joining(","));

        for (String ids : List.of("", many)) {
            MvcTestResult result = put(operator(), academyId, "{\"studentIds\":[" + ids + "],\"enabled\":true}");
            assertThat(result).hasStatus(400);
            assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("VALIDATION");
            assertThat(result).bodyJson().extractingPath("$.errors[0].field").isEqualTo("studentIds");
        }
    }

    @Test
    void 관리자_교사는_403() {
        long adminId = data.staff(academyId, "관리자", "admin");
        long teacherId = data.staff(academyId, "교사", "teacher");
        String body = "{\"studentIds\":[" + ga + "],\"enabled\":true}";

        for (Cookie staff : List.of(
                cookies.access(adminId, UserRole.ADMIN, academyId),
                cookies.access(teacherId, UserRole.TEACHER, academyId))) {
            assertThat(list(staff, academyId)).hasStatus(403);
            assertThat(put(staff, academyId, body)).hasStatus(403);
        }
    }

    @Test
    void 비활성_운영자는_401() {
        jdbc.update("update `user` set status = 'inactive' where id = ?", operatorId);

        assertThat(list(operator(), academyId)).hasStatus(401);
        assertThat(put(operator(), academyId, "{\"studentIds\":[" + ga + "],\"enabled\":true}"))
                .hasStatus(401);
    }

    @Test
    void 없는_학원은_404() {
        assertThat(list(operator(), 999_999_999L)).hasStatus(404);
        assertThat(put(operator(), 999_999_999L, "{\"studentIds\":[" + ga + "],\"enabled\":true}"))
                .hasStatus(404);
        assertThat(count("select count(*) from talk_student_access where student_id = ?", ga))
                .isZero();
    }

    private Cookie operator() {
        return cookies.operatorAccess(operatorId);
    }

    private MvcTestResult list(Cookie access, long academy) {
        return mvc.get()
                .uri("/api/v1/system/academies/" + academy + "/talk/students")
                .cookie(access)
                .exchange();
    }

    private MvcTestResult put(Cookie access, long academy, String body) {
        return mvc.put()
                .uri("/api/v1/system/academies/" + academy + "/talk/students")
                .cookie(access, AuthCookieSupport.csrf())
                .header(AuthCookieSupport.CSRF_HEADER, AuthCookieSupport.CSRF_VALUE)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();
    }

    private int count(String sql, Object... args) {
        Integer value = jdbc.queryForObject(sql, Integer.class, args);
        return value == null ? 0 : value;
    }
}
