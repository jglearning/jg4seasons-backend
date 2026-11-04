package com.jglearning.jg4seasons.auth.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.jglearning.jg4seasons.IntegrationTest;
import com.jglearning.jg4seasons.TestData;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.annotation.Transactional;

/** 1.1 POST /auth/code/verify — 가입 코드로 반 확인. 명단 없음, IP당 1분 10회. */
@IntegrationTest
@Transactional
class CodeVerifyTest {

    /** 요청 한도 카운터는 컨텍스트에 남는다 → 테스트마다 다른 IP */
    static final AtomicInteger IP_SEQ = new AtomicInteger(1);

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcTemplate jdbc;

    TestData data;
    long academyId;
    String code;
    long classId;
    String ip;

    @BeforeEach
    void setUp() {
        data = new TestData(jdbc);
        academyId = data.academy("테스트 학원");
        code = TestData.uniqueAccessCode();
        classId = data.clazz(academyId, "L2-B1", "겨울 그린 A반", code, "active");
        data.enroll(classId, data.student(academyId, "김학생"));
        ip = "198.51.100." + IP_SEQ.getAndIncrement();
    }

    private MvcTestResult verify(String body, String fromIp) {
        return mvc.post()
                .uri("/api/v1/auth/code/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .with(request -> {
                    request.setRemoteAddr(fromIp);
                    return request;
                })
                .exchange();
    }

    private MvcTestResult verify(String code) {
        return verify("{\"code\":\"" + code + "\"}", ip);
    }

    @Test
    void 가입_코드로_반_정보를_받는다() {
        MvcTestResult result = verify(code);

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.class.id").asNumber().isEqualTo((int) classId);
        assertThat(result).bodyJson().extractingPath("$.class.name").isEqualTo("겨울 그린 A반");
        assertThat(result).bodyJson().extractingPath("$.class.academyName").isEqualTo("테스트 학원");
        assertThat(result).bodyJson().extractingPath("$.class.level.code").isEqualTo("L2");
        assertThat(result).bodyJson().extractingPath("$.class.level.nameKo").isEqualTo("그린");
        assertThat(result).bodyJson().extractingPath("$.class.level.colorHex").isEqualTo("#1F7A4F");
        assertThat(result).bodyJson().extractingPath("$.class.book.code").isEqualTo("L2-B1");
        assertThat(result).bodyJson().extractingPath("$.class.book.season").isEqualTo("겨울");
    }

    @Test
    void 학생_명단은_내려주지_않는다() throws Exception {
        String body = verify(code).getResponse().getContentAsString();

        assertThat(body).doesNotContain("김학생").doesNotContain("students");
    }

    @Test
    void 소문자와_앞뒤_공백도_받는다() {
        assertThat(verify("  " + code.toLowerCase() + " ")).hasStatusOk();
    }

    @Test
    void CSRF_헤더_없이도_된다() {
        assertThat(verify(code)).hasStatusOk();
    }

    @Test
    void 없는_코드는_404_INVALID_CODE() {
        MvcTestResult result = verify(TestData.uniqueAccessCode());

        assertThat(result).hasStatus(404).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("INVALID_CODE");
        assertThat(result).bodyJson().extractingPath("$.title").isEqualTo("가입 코드를 찾을 수 없어요");
    }

    @Test
    void 비활성_반은_404() {
        String finished = TestData.uniqueAccessCode();
        data.clazz(academyId, "L2-B1", "끝난 반", finished, "finished");

        assertThat(verify(finished)).hasStatus(404);
    }

    @Test
    void 정지된_학원의_반은_404() {
        jdbc.update("update academy set status = 'suspended' where id = ?", academyId);

        MvcTestResult result = verify(code);

        assertThat(result).hasStatus(404);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("INVALID_CODE");
    }

    @Test
    void 형식이_틀리면_400_VALIDATION() {
        assertThat(verify("G7KM")).hasStatus(400);
        assertThat(verify("G7KM-2XP")).hasStatus(400);
        MvcTestResult number = verify("{\"code\":12345678}", ip);
        assertThat(number).hasStatus(400);
        assertThat(number).bodyJson().extractingPath("$.code").isEqualTo("VALIDATION");
    }

    @Test
    void 같은_IP_11번째는_429_RATE_LIMITED() {
        for (int i = 0; i < 10; i++) {
            assertThat(verify(code)).as("%d번째", i + 1).hasStatusOk();
        }
        MvcTestResult limited = verify(code);

        assertThat(limited).hasStatus(429);
        assertThat(limited).bodyJson().extractingPath("$.code").isEqualTo("RATE_LIMITED");
        assertThat(verify("{\"code\":\"" + code + "\"}", "198.51.100.250"))
                .as("다른 IP")
                .hasStatusOk();
    }

    @Test
    void 없는_코드도_한도에_센다() {
        for (int i = 0; i < 10; i++) {
            verify(TestData.uniqueAccessCode());
        }
        assertThat(verify(code)).hasStatus(429);
    }
}
