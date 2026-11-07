package com.jglearning.jg4seasons.academy;

import static org.assertj.core.api.Assertions.assertThat;

import com.jglearning.jg4seasons.IntegrationTest;
import com.jglearning.jg4seasons.TestData;
import com.jglearning.jg4seasons.auth.api.StaffAccounts;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.transaction.annotation.Transactional;

/**
 * 첫 학원·관리자 부트스트랩(SYS-01). 학원이 하나도 없을 때만 설정값으로 만든다 — 서버를 다시 띄워도 또 만들거나 비밀번호를 바꾸지 않는다.
 *
 * <p>테스트 DB는 다른 테스트의 학원이 있어 "빈 DB"를 만들 수 없다. 건너뛰는 경로는 createIfNoAcademy로, 만드는 경로는 create로 본다.
 */
@IntegrationTest
@Transactional
@ExtendWith(OutputCaptureExtension.class)
class InitialAcademyInitializerTest {

    static final String PASSWORD = "first-admin-password";
    static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired
    AcademyRepository academies;

    @Autowired
    StaffAccounts staffAccounts;

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcTemplate jdbc;

    private InitialAcademyInitializer initializer(InitialAcademyProperties properties) {
        return new InitialAcademyInitializer(academies, staffAccounts, properties);
    }

    private static String unique(String prefix) {
        return prefix + SEQ.incrementAndGet() + "-" + System.nanoTime() % 100_000;
    }

    private Integer count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    @Test
    void 학원과_관리자를_만들고_그_아이디로_로그인된다(CapturedOutput output) {
        String slug = unique("init");
        String loginId = unique("owner");
        InitialAcademy initial = new InitialAcademyProperties("첫 학원", slug, loginId, PASSWORD, "원장", true)
                .resolve()
                .orElseThrow();

        initializer(null).create(initial);

        Map<String, Object> academy = jdbc.queryForMap(
                "select id, name, status, settings->>'$.timezone' as timezone from academy where slug = ?", slug);
        assertThat(academy)
                .containsEntry("name", "첫 학원")
                .containsEntry("status", "active")
                .containsEntry("timezone", "Asia/Seoul");
        long academyId = ((Number) academy.get("id")).longValue();
        assertThat(count(
                        "select count(*) from `user` where academy_id = ? and role = 'admin' and name = '원장'"
                                + " and status = 'active'",
                        academyId))
                .isEqualTo(1);
        assertThat(mvc.post()
                        .uri("/api/v1/auth/staff/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\":\"" + loginId + "\",\"password\":\"" + PASSWORD + "\"}")
                        .with(request -> {
                            request.setRemoteAddr("198.18.1." + (SEQ.get() % 250 + 1));
                            return request;
                        })
                        .exchange())
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.user.role")
                .isEqualTo("admin");
        // 만든 사실만 남기고 비밀번호는 남기지 않는다
        assertThat(output).contains("첫 학원과 관리자를 만들었다").doesNotContain(PASSWORD);
    }

    // 운영 서버를 다시 띄울 때 관리자가 또 생기거나 비밀번호가 바뀌면 안 된다
    @Test
    void 학원이_하나라도_있으면_아무것도_만들지_않는다() {
        new TestData(jdbc).academy("이미 있는 학원");
        int academiesBefore = count("select count(*) from academy");
        int usersBefore = count("select count(*) from `user`");
        String slug = unique("skip");

        initializer(new InitialAcademyProperties("첫 학원", slug, unique("owner"), PASSWORD, null, true))
                .createIfNoAcademy();

        assertThat(count("select count(*) from academy where slug = ?", slug)).isZero();
        assertThat(count("select count(*) from academy")).isEqualTo(academiesBefore);
        assertThat(count("select count(*) from `user`")).isEqualTo(usersBefore);
    }

    // 학원이 있으면 설정값을 읽지 않는다 — 실제 사용 중 파라미터를 지워도(반쪽이 돼도) 서버가 뜬다
    @Test
    void 학원이_있으면_설정이_반쪽이어도_검사하지_않는다() {
        new TestData(jdbc).academy("이미 있는 학원");

        initializer(new InitialAcademyProperties(null, null, "owner", null, null, true))
                .createIfNoAcademy();
    }
}
