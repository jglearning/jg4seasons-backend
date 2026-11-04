package com.jglearning.jg4seasons.auth.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.jglearning.jg4seasons.IntegrationTest;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** 학생 입장·세션 API 계약(03 API명세 §1). 프론트가 openapi.json에서 타입을 만든다. */
@IntegrationTest
class AuthContractTest {

    @Autowired
    MockMvcTester mvc;

    private MvcTestResult apiDocs() {
        return mvc.get().uri("/v3/api-docs").exchange();
    }

    @Test
    void 학생_입장과_세션_경로가_명세에_있다() {
        MvcTestResult docs = apiDocs();
        assertThat(docs)
                .bodyJson()
                .extractingPath("$.paths['/api/v1/auth/code/verify'].post")
                .isNotNull();
        assertThat(docs)
                .bodyJson()
                .extractingPath("$.paths['/api/v1/auth/student/login'].post")
                .isNotNull();
        assertThat(docs)
                .bodyJson()
                .extractingPath("$.paths['/api/v1/auth/refresh'].post")
                .isNotNull();
        assertThat(docs)
                .bodyJson()
                .extractingPath("$.paths['/api/v1/auth/logout'].post")
                .isNotNull();
        assertThat(docs).bodyJson().extractingPath("$.paths['/api/v1/me'].get").isNotNull();
    }

    @Test
    void 로그아웃은_본문_없는_204다() {
        assertThat(apiDocs())
                .bodyJson()
                .extractingPath("$.paths['/api/v1/auth/logout'].post.responses['204']")
                .isNotNull();
    }

    @Test
    void 개인_코드는_숫자_4자리_문자열이다() {
        MvcTestResult docs = apiDocs();
        assertThat(docs)
                .bodyJson()
                .extractingPath("$.components.schemas.StudentLoginRequest.required")
                .asArray()
                .containsExactlyInAnyOrder("code", "personalCode");
        assertThat(docs)
                .bodyJson()
                .extractingPath("$.components.schemas.StudentLoginRequest.properties.personalCode.type")
                .isEqualTo("string");
        assertThat(docs)
                .bodyJson()
                .extractingPath("$.components.schemas.StudentLoginRequest.properties.personalCode.pattern")
                .isEqualTo("^[0-9]{4}$");
    }

    @Test
    void 가입_코드_확인_응답은_class_필드로_반_정보를_준다() {
        MvcTestResult docs = apiDocs();
        assertThat(docs)
                .bodyJson()
                .extractingPath("$.components.schemas.CodeVerifyResponse.required")
                .asArray()
                .containsExactly("class");
        assertThat(docs)
                .bodyJson()
                .extractingPath("$.components.schemas.ClassInfo.required")
                .asArray()
                .containsExactlyInAnyOrder("id", "name", "academyName", "level", "book");
    }

    @Test
    void 응답_스키마에_토큰_필드가_없다() throws Exception {
        String json = apiDocs().getResponse().getContentAsString(StandardCharsets.UTF_8);
        Matcher names = Pattern.compile("\"(access|refresh|csrf|token|accessToken|refreshToken)\"\\s*:")
                .matcher(json);
        assertThat(names.find()).as("토큰은 쿠키로만 나간다(ADR-18)").isFalse();
    }
}
