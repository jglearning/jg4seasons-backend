package com.jglearning.jg4seasons.admin.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.jglearning.jg4seasons.IntegrationTest;
import com.jglearning.jg4seasons.MutableClock;
import com.jglearning.jg4seasons.TestData;
import com.jglearning.jg4seasons.auth.AuthCookieSupport;
import com.jglearning.jg4seasons.auth.UserRole;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * 실제로 커밋되는 명단 가져오기(ADR 0036): 뒤쪽 반 항목에서 실패하면 앞에서 만든 반·진도표·학생·코드·소속·감사가 모두 남지 않는다. 테스트 트랜잭션 안에서는 실패한
 * 요청의 쓰기가 같은 트랜잭션에 보여 확인할 수 없어 커밋하고, 넣은 데이터는 끝에 지운다. 오늘 = 서울 2026-11-10.
 */
@IntegrationTest
class RosterImportCommitTest {

    static final Instant NOON_KST = Instant.parse("2026-11-10T03:00:00Z");
    static final LocalDate TODAY = LocalDate.of(2026, 11, 10);

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
    long adminId;
    long greenId;

    @BeforeEach
    void setUp() {
        clock.set(NOON_KST);
        data = new TestData(jdbc);
        academyId = data.academy("가져오기 커밋 학원");
        adminId = data.staff(academyId, "관리자", "admin");
        greenId = data.clazz(academyId, "L2-B1", "그린 월수반", TestData.uniqueAccessCode(), "active");
    }

    @AfterEach
    void cleanUp() {
        clock.reset();
        String classes = "(select id from (select id from `class` where academy_id = " + academyId + ") c)";
        String users = "(select id from (select id from `user` where academy_id = " + academyId + ") u)";
        jdbc.update("delete from enrollment where class_id in " + classes);
        jdbc.update("delete from class_session where class_id in " + classes);
        jdbc.update("delete from class_teacher where class_id in " + classes);
        jdbc.update("delete from auth_identity where user_id in " + users);
        jdbc.update("delete from audit_log where academy_id = ?", academyId);
        jdbc.update("delete from book_license where academy_id = ?", academyId);
        jdbc.update("delete from `class` where academy_id = ?", academyId);
        jdbc.update("delete from `user` where academy_id = ?", academyId);
        jdbc.update("delete from academy where id = ?", academyId);
    }

    private MvcTestResult create(String classesJson) {
        return mvc.post()
                .uri("/api/v1/admin/roster-imports")
                .cookie(cookies.access(adminId, UserRole.ADMIN, academyId), AuthCookieSupport.csrf())
                .header(AuthCookieSupport.CSRF_HEADER, AuthCookieSupport.CSRF_VALUE)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"classes\":" + classesJson + "}")
                .exchange();
    }

    private String newClass(String name, String bookCode) {
        return "{\"name\":\"" + name + "\",\"bookCode\":\"" + bookCode
                + "\",\"startDate\":\"2026-12-01\",\"weekdays\":[2,4],\"teacherIds\":[]}";
    }

    private long count(String sql) {
        Long n = jdbc.queryForObject(sql, Long.class);
        return n == null ? 0 : n;
    }

    /** 이 학원의 반·진도표·학생·개인 코드·소속·감사 수 */
    private String snapshot() {
        String classes = "(select id from `class` where academy_id = " + academyId + ")";
        String users = "(select id from `user` where academy_id = " + academyId + ")";
        return "class=" + count("select count(*) from `class` where academy_id = " + academyId)
                + " session=" + count("select count(*) from class_session where class_id in " + classes)
                + " user=" + count("select count(*) from `user` where academy_id = " + academyId)
                + " identity=" + count("select count(*) from auth_identity where user_id in " + users)
                + " enrollment=" + count("select count(*) from enrollment where class_id in " + classes)
                + " audit=" + count("select count(*) from audit_log where academy_id = " + academyId);
    }

    @Test
    void 뒤쪽_반이_끝난_반이면_409이고_앞의_새_반도_남지_않는다() {
        long finished = data.clazz(academyId, "L2-B1", "지난 반", TestData.uniqueAccessCode(), "finished");
        String before = snapshot();

        MvcTestResult result = create("[{\"newClass\":" + newClass("블루 화목반", "L3-B1")
                + ",\"students\":[{\"name\":\"이서연\"}]},"
                + "{\"existingClassId\":" + finished + ",\"students\":[{\"name\":\"김민준\"}]}]");

        assertThat(result).hasStatus(409);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("CLASS_NOT_ACTIVE");
        assertThat(snapshot()).isEqualTo(before);
    }

    @Test
    void 합친_학생_수가_한도를_넘으면_409이고_첫_반도_남지_않는다() {
        data.enforceLicenses(academyId);
        data.license(academyId, "L2-B1", TODAY.minusDays(9), TODAY.plusDays(60), 3);
        data.enroll(greenId, data.student(academyId, "이미 다니는 학생"));
        String before = snapshot();

        // 지금 1 + 1 = 2(통과) → + 2 = 4 > 3
        MvcTestResult result = create("[{\"existingClassId\":" + greenId + ",\"students\":[{\"name\":\"가\"}]},"
                + "{\"newClass\":" + newClass("그린 화목반", "L2-B1")
                + ",\"students\":[{\"name\":\"나\"},{\"name\":\"다\"}]}]");

        assertThat(result).hasStatus(409);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("LICENSE_SEATS_FULL");
        assertThat(snapshot()).isEqualTo(before);
    }

    @Test
    void 뒤쪽_반의_권이_없으면_400이고_아무것도_만들지_않는다() {
        String before = snapshot();

        MvcTestResult result = create("[{\"newClass\":" + newClass("블루 화목반", "L3-B1")
                + ",\"students\":[{\"name\":\"이서연\"}]},"
                + "{\"newClass\":" + newClass("없는 권 반", "L9-B9") + ",\"students\":[{\"name\":\"김민준\"}]}]");

        assertThat(result).hasStatus(400);
        assertThat(result).bodyJson().extractingPath("$.errors[0].field").isEqualTo("classes[1].newClass.bookCode");
        assertThat(snapshot()).isEqualTo(before);
    }

    @Test
    void 다른_학원_교사를_넣으면_404이고_반도_교사_배정도_남지_않는다() {
        long other = new TestData(jdbc).academy("다른 커밋 학원");
        long otherTeacher = new TestData(jdbc).staff(other, "다른 교사", "teacher");
        String before = snapshot();
        try {
            MvcTestResult result = create("[{\"newClass\":" + newClass("블루 화목반", "L3-B1")
                    + ",\"students\":[{\"name\":\"이서연\"}]},"
                    + "{\"newClass\":{\"name\":\"교사 반\",\"bookCode\":\"L3-B1\",\"startDate\":\"2026-12-01\","
                    + "\"weekdays\":[2,4],\"teacherIds\":[" + otherTeacher + "]},\"students\":[{\"name\":\"김민준\"}]}]");

            assertThat(result).hasStatus(404);
            assertThat(snapshot()).isEqualTo(before);
            assertThat(count("select count(*) from class_teacher where user_id = " + otherTeacher))
                    .isZero();
        } finally {
            jdbc.update("delete from `user` where academy_id = ?", other);
            jdbc.update("delete from academy where id = ?", other);
        }
    }

    @Test
    void 성공하면_반_학생_코드_소속_감사가_커밋된다() {
        MvcTestResult result = create("[{\"newClass\":" + newClass("블루 화목반", "L3-B1")
                + ",\"students\":[{\"name\":\"이서연\"},{\"name\":\"박지호\"}]}]");

        assertThat(result).hasStatus(201);
        String classes = "(select id from `class` where academy_id = " + academyId + ")";
        String users = "(select id from `user` where academy_id = " + academyId + " and role = 'student')";
        assertThat(count("select count(*) from `class` where academy_id = " + academyId))
                .isEqualTo(2); // 그린 + 새 반
        assertThat(count("select count(*) from class_session where class_id in " + classes))
                .isEqualTo(24);
        assertThat(count("select count(*) from `user` where academy_id = " + academyId + " and role = 'student'"))
                .isEqualTo(2);
        assertThat(count("select count(*) from auth_identity where user_id in " + users))
                .isEqualTo(2);
        assertThat(count("select count(*) from enrollment where class_id in " + classes))
                .isEqualTo(2);
        assertThat(count("select count(*) from audit_log where academy_id = " + academyId
                        + " and action = 'ROSTER_IMPORT'"))
                .isEqualTo(1);
    }
}
