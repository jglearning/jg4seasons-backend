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
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.annotation.Transactional;

/**
 * 권별 학생 수 한도(ADR 0031 결정 5): 그 학원·그 권의 active 반에 현재 소속된 서로 다른 학생 ∪ 들어올 학생 > 한도면 등록(5.2)·이동 409
 * LICENSE_SEATS_FULL, 사용 기간이 아니면 403. 퇴원·재발급은 만료 뒤에도 된다. 사용권을 적용하는 학원, 오늘 = 서울 2026-11-10. 사용권은 첫 요청 전에 넣는다(한
 * 테스트 = 한 영속성 컨텍스트). 409 뒤 "아무도 만들지 않음"과 동시 등록은 LicenseSeatsCommitTest.
 */
@IntegrationTest
@Transactional
class LicenseSeatsTest {

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
    /** L2-B1 반 둘 */
    long classA;

    long classB;
    /** L2-B2 반 */
    long classC;

    @BeforeEach
    void setUp() {
        clock.set(NOON_KST);
        data = new TestData(jdbc);
        academyId = data.academy("연계 학원");
        data.enforceLicenses(academyId);
        adminId = data.staff(academyId, "관리자", "admin");
        classA = data.clazz(academyId, "L2-B1", "A반", TestData.uniqueAccessCode(), "active");
        classB = data.clazz(academyId, "L2-B1", "B반", TestData.uniqueAccessCode(), "active");
        classC = data.clazz(academyId, "L2-B2", "C반", TestData.uniqueAccessCode(), "active");
    }

    @AfterEach
    void resetClock() {
        clock.reset();
    }

    private void license(String bookCode, int seats) {
        data.license(academyId, bookCode, TODAY.minusDays(9), TODAY.plusDays(60), seats);
    }

    private void expiredLicense(String bookCode) {
        data.license(academyId, bookCode, TODAY.minusDays(60), TODAY.minusDays(1), 30);
    }

    /** 반에 이미 있는 학생 */
    private long member(long classId, String name) {
        long id = data.student(academyId, name);
        data.enroll(classId, id);
        return id;
    }

    private MvcTestResult send(HttpMethod method, String uri, String body) {
        return mvc.method(method)
                .uri(uri)
                .cookie(cookies.access(adminId, UserRole.ADMIN, academyId), AuthCookieSupport.csrf())
                .header(AuthCookieSupport.CSRF_HEADER, AuthCookieSupport.CSRF_VALUE)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();
    }

    private MvcTestResult register(long classId, String... names) {
        StringBuilder students = new StringBuilder();
        for (String name : names) {
            students.append(students.isEmpty() ? "" : ",")
                    .append("{\"name\":\"")
                    .append(name)
                    .append("\"}");
        }
        return send(
                HttpMethod.POST, "/api/v1/admin/classes/" + classId + "/students", "{\"students\":[" + students + "]}");
    }

    private MvcTestResult move(long studentId, long toClassId) {
        return send(
                HttpMethod.POST, "/api/v1/admin/students/" + studentId + "/move", "{\"toClassId\":" + toClassId + "}");
    }

    private MvcTestResult withdraw(long studentId) {
        return send(HttpMethod.PATCH, "/api/v1/admin/students/" + studentId, "{\"status\":\"inactive\"}");
    }

    private static void assertSeatsFull(MvcTestResult result) {
        assertThat(result).hasStatus(409);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("LICENSE_SEATS_FULL");
    }

    private static void assertInactive(MvcTestResult result) {
        assertThat(result).hasStatus(403);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("LICENSE_INACTIVE");
    }

    @Test
    void 한도까지는_등록된다() {
        license("L2-B1", 2);

        assertThat(register(classA, "김하나", "김둘")).hasStatus(201);
    }

    @Test
    void 한도를_넘는_묶음_등록은_409() {
        license("L2-B1", 2);
        member(classA, "기존");

        assertSeatsFull(register(classA, "김하나", "김둘"));
    }

    @Test
    void 같은_권의_다른_반_학생도_센다() {
        license("L2-B1", 1);
        member(classB, "B반 학생");

        assertSeatsFull(register(classA, "김하나"));
    }

    @Test
    void 같은_권_두_반에_함께_속한_학생은_한_자리로_센다() {
        license("L2-B1", 2);
        long both = member(classA, "두 반");
        data.enroll(classB, both);

        assertThat(register(classA, "김하나")).hasStatus(201);
    }

    @Test
    void 만료된_권_반에서_다른_권_반으로_이동은_된다() {
        expiredLicense("L2-B1");
        license("L2-B2", 30);
        long student = member(classA, "옮김");

        assertThat(move(student, classC)).hasStatusOk();
    }

    @Test
    void 같은_권_반끼리_이동은_자리를_더_쓰지_않는다() {
        license("L2-B1", 2);
        long student = member(classA, "이동");
        member(classA, "남음");

        assertThat(move(student, classB)).hasStatusOk();
    }

    @Test
    void 다른_권_반으로_이동은_받는_권_한도를_본다() {
        license("L2-B1", 30);
        license("L2-B2", 1);
        member(classC, "C반 학생");
        long student = member(classA, "이동");

        assertSeatsFull(move(student, classC));
    }

    @Test
    void 받는_권이_만료면_이동은_403() {
        license("L2-B1", 30);
        expiredLicense("L2-B2");
        long student = member(classA, "이동");

        assertInactive(move(student, classC));
    }

    @Test
    void 퇴원하면_자리가_빈다() {
        license("L2-B1", 1);
        long leaving = member(classA, "퇴원");

        assertThat(withdraw(leaving)).hasStatusOk();
        assertThat(register(classA, "새 학생")).hasStatus(201);
    }

    @Test
    void 끝난_반_학생은_세지_않는다() {
        license("L2-B1", 1);
        long finished = data.clazz(academyId, "L2-B1", "끝난 반", TestData.uniqueAccessCode(), "finished");
        member(finished, "지난 학기");

        assertThat(register(classA, "김하나")).hasStatus(201);
    }

    @Test
    void 다른_학원_학생은_세지_않는다() {
        license("L2-B1", 1);
        long other = data.academy("다른 학원");
        long otherClass = data.clazz(other, "L2-B1", "다른 학원 반", TestData.uniqueAccessCode(), "active");
        data.enroll(otherClass, data.student(other, "다른 학원 학생"));

        assertThat(register(classA, "김하나")).hasStatus(201);
    }

    @Test
    void 한도보다_많아져도_기존_학생은_같은_권_반끼리_옮기고_새_등록만_막는다() {
        // 한도를 낮춰 지금 학생(2명)이 한도(1)보다 많은 상태
        license("L2-B1", 1);
        long moving = member(classA, "하나");
        member(classA, "둘");

        assertThat(move(moving, classB)).as("자리가 늘지 않는 이동").hasStatusOk();
        assertSeatsFull(register(classA, "셋"));
    }

    @Test
    void 만료된_권_반에_이미_있는_학생이_다른_반에서_나가기만_하는_이동은_된다() {
        expiredLicense("L2-B1");
        long student = member(classA, "두 반");
        data.enroll(classB, student);

        assertThat(move(student, classB)).hasStatusOk();
    }

    @Test
    void 만료된_권이어도_이미_그_반에만_있으면_ALREADY_IN_CLASS() {
        expiredLicense("L2-B1");
        long student = member(classA, "한 반");

        MvcTestResult result = move(student, classA);

        assertThat(result).hasStatus(409);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("ALREADY_IN_CLASS");
    }

    private MvcTestResult changeClass(long classId, String body) {
        return send(HttpMethod.PATCH, "/api/v1/admin/classes/" + classId, body);
    }

    private String statusOf(long classId) {
        return jdbc.queryForObject("select status from `class` where id = ?", String.class, classId);
    }

    @Test
    void 끝난_반을_되살려_한도를_넘으면_409이고_상태는_그대로() {
        license("L2-B1", 1);
        member(classA, "지금 반");
        long finished = data.clazz(academyId, "L2-B1", "끝난 반", TestData.uniqueAccessCode(), "finished");
        member(finished, "지난 학기");

        assertSeatsFull(changeClass(finished, "{\"status\":\"active\"}"));
        assertThat(statusOf(finished)).isEqualTo("finished");
    }

    @Test
    void 한도_안이면_끝난_반을_되살린다() {
        license("L2-B1", 2);
        member(classA, "지금 반");
        long finished = data.clazz(academyId, "L2-B1", "끝난 반", TestData.uniqueAccessCode(), "finished");
        member(finished, "지난 학기");

        assertThat(changeClass(finished, "{\"status\":\"active\"}")).hasStatusOk();
    }

    @Test
    void 만료된_권의_끝난_반은_되살리지_못한다() {
        expiredLicense("L2-B1");
        long finished = data.clazz(academyId, "L2-B1", "끝난 반", TestData.uniqueAccessCode(), "finished");

        assertInactive(changeClass(finished, "{\"status\":\"active\"}"));
        assertThat(statusOf(finished)).isEqualTo("finished");
    }

    @Test
    void 이미_진행_중인_반은_한도를_넘어도_status_active와_이름을_바꾼다() {
        license("L2-B1", 1);
        member(classA, "하나");
        member(classA, "둘");

        assertThat(changeClass(classA, "{\"name\":\"바뀐 반\",\"status\":\"active\"}"))
                .hasStatusOk();
    }

    @Test
    void 만료된_권의_반도_끝낼_수는_있다() {
        expiredLicense("L2-B1");

        assertThat(changeClass(classA, "{\"status\":\"finished\"}")).hasStatusOk();
    }

    @Test
    void 만료_권_반으로_등록은_403() {
        expiredLicense("L2-B1");

        assertInactive(register(classA, "김하나"));
    }

    @Test
    void 시작_전_권_반으로도_한도까지_등록한다() {
        data.license(academyId, "L2-B1", TODAY.plusDays(1), TODAY.plusDays(60), 1);

        assertThat(register(classA, "김하나")).hasStatus(201);
    }

    @Test
    void 만료_뒤에도_퇴원과_개인_코드_재발급은_된다() {
        expiredLicense("L2-B1");
        long student = member(classA, "만료");
        long other = member(classA, "퇴원");

        assertThat(send(
                        HttpMethod.POST,
                        "/api/v1/admin/classes/" + classA + "/students/personal-codes/regenerate",
                        "{\"studentIds\":[" + student + "]}"))
                .hasStatusOk();
        assertThat(withdraw(other)).hasStatusOk();
    }

    @Test
    void 사용권을_적용하지_않는_학원은_세지_않는다() {
        jdbc.update("update academy set license_enforced = false where id = ?", academyId);

        assertThat(register(classA, "하나", "둘", "셋")).hasStatus(201);
    }
}
