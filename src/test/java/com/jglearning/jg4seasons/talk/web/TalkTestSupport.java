package com.jglearning.jg4seasons.talk.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.jglearning.jg4seasons.auth.AuthCookieSupport;
import jakarta.servlet.http.Cookie;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** 프리토킹 API 테스트 공용: 학원 설정·요청·오류 확인 */
final class TalkTestSupport {

    static final String CONFIG = "/api/v1/talk/config";
    static final String SESSIONS = "/api/v1/talk/sessions";

    private TalkTestSupport() {}

    /** 켜기·구독 값. talk가 false면 features에서 빼 둔다(기본 꺼짐과 같게) */
    static void features(JdbcTemplate jdbc, long academyId, boolean talk, boolean entitled) {
        String features = talk ? "{\"talk\":true,\"talkEntitled\":" + entitled + "}" : "{}";
        jdbc.update(
                "update academy set settings = ? where id = ?",
                "{\"timezone\":\"Asia/Seoul\",\"features\":" + features + "}",
                academyId);
    }

    static MvcTestResult config(MockMvcTester mvc, Cookie access) {
        return mvc.get().uri(CONFIG).cookie(access).exchange();
    }

    static MvcTestResult create(MockMvcTester mvc, Cookie access, String clientId, String topic, String voice) {
        return createRaw(
                mvc,
                access,
                "{\"clientSessionId\":\"" + clientId + "\",\"topic\":\"" + topic + "\",\"voice\":\"" + voice + "\"}");
    }

    static MvcTestResult createRaw(MockMvcTester mvc, Cookie access, String body) {
        return mvc.post()
                .uri(SESSIONS)
                .cookie(access, AuthCookieSupport.csrf())
                .header(AuthCookieSupport.CSRF_HEADER, AuthCookieSupport.CSRF_VALUE)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();
    }

    static void assertProblem(MvcTestResult result, HttpStatus status, String code) {
        assertThat(result).hasStatus(status);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo(code);
    }
}
