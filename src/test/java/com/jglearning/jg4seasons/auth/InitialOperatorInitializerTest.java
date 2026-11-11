package com.jglearning.jg4seasons.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.jglearning.jg4seasons.IntegrationTest;
import com.jglearning.jg4seasons.TestData;
import com.jglearning.jg4seasons.auth.persistence.PreAuthUserLookup;
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
 * 운영자 계정 부트스트랩(ADR 0032). 운영자가 한 명도 없을 때만 설정값으로 만든다 — 서버를 다시 띄워도 또 만들거나 비밀번호를 바꾸지 않는다.
 *
 * <p>테스트 DB에 다른 테스트의 운영자가 있을 수 있어 "운영자 0명"을 만들 수 없다. 건너뛰는 경로는 createIfNoOperator로, 만드는 경로는 create로 본다.
 */
@IntegrationTest
@Transactional
@ExtendWith(OutputCaptureExtension.class)
class InitialOperatorInitializerTest {

    static final String PASSWORD = "operator-pass-1";
    static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired
    StaffAccountService staffAccounts;

    @Autowired
    PreAuthUserLookup preAuthUsers;

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcTemplate jdbc;

    private InitialOperatorInitializer initializer(InitialOperatorProperties properties) {
        return new InitialOperatorInitializer(preAuthUsers, staffAccounts, properties);
    }

    private static String unique() {
        return "ops" + SEQ.incrementAndGet() + "-" + System.nanoTime() % 100_000;
    }

    private Integer count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    @Test
    void 운영자를_만들고_그_아이디로_로그인된다(CapturedOutput output) {
        String loginId = unique();
        InitialOperator operator =
                new InitialOperatorProperties(loginId, PASSWORD, null).resolve().orElseThrow();

        initializer(null).create(operator);

        assertThat(count("select count(*) from `user` where academy_id is null and role = 'superadmin'"
                        + " and name = '운영자' and status = 'active'"))
                .isPositive();
        assertThat(mvc.post()
                        .uri("/api/v1/auth/staff/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\":\"" + loginId + "\",\"password\":\"" + PASSWORD + "\"}")
                        .with(request -> {
                            request.setRemoteAddr("198.18.2." + (SEQ.get() % 250 + 1));
                            return request;
                        })
                        .exchange())
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.user.role")
                .isEqualTo("superadmin");
        assertThat(output).contains("운영자 계정을 만들었다").doesNotContain(PASSWORD);
    }

    // 운영 서버를 다시 띄울 때 운영자가 또 생기거나 비밀번호가 바뀌면 안 된다
    @Test
    void 운영자가_있으면_아무것도_만들지_않는다() {
        new TestData(jdbc).operator("이미 있는 운영자");
        int usersBefore = count("select count(*) from `user`");
        String loginId = unique();

        initializer(new InitialOperatorProperties(loginId, PASSWORD, null)).createIfNoOperator();

        assertThat(count("select count(*) from `user`")).isEqualTo(usersBefore);
        assertThat(count("select count(*) from auth_identity where identifier = ?", loginId))
                .isZero();
    }

    // 운영자 관리 화면이 없어 비활성 운영자만 남으면 시스템 API를 아무도 못 쓴다 — 조용히 넘어가지 않게(리뷰 #47-11)
    @Test
    void 비활성_운영자만_있으면_WARN을_남긴다(CapturedOutput output) {
        long operatorId = new TestData(jdbc).operator("멈춘 운영자");
        jdbc.update("update `user` set status = 'inactive' where id = ?", operatorId);

        initializer(new InitialOperatorProperties(null, null, null)).createIfNoOperator();

        assertThat(output).contains("활성 운영자가 없다");
    }

    // 운영자가 있는데 설정값을 바꿔도 비밀번호 재설정이 아니다 — 무시한 사실은 남기고 값은 남기지 않는다
    @Test
    void 운영자가_있는데_설정값이_있으면_쓰지_않았다고_남긴다(CapturedOutput output) {
        new TestData(jdbc).operator("이미 있는 운영자");

        initializer(new InitialOperatorProperties(unique(), PASSWORD, null)).createIfNoOperator();

        assertThat(output).contains("설정값을 쓰지 않았다").doesNotContain(PASSWORD);
    }

    // 운영자가 있으면 설정값을 읽지 않는다 — 파라미터를 지워도(반쪽이 돼도) 서버가 뜬다
    @Test
    void 운영자가_있으면_설정이_반쪽이어도_검사하지_않는다() {
        new TestData(jdbc).operator("이미 있는 운영자");

        initializer(new InitialOperatorProperties("ops", null, null)).createIfNoOperator();
    }
}
