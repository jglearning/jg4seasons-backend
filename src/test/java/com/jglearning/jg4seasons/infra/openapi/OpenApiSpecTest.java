package com.jglearning.jg4seasons.infra.openapi;

import static org.assertj.core.api.Assertions.assertThat;

import com.jglearning.jg4seasons.IntegrationTest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * docs/api/openapi.json은 API 계약이다. 컨트롤러를 바꾸면 이 테스트가 실패한다.
 *
 * <p>의도한 변경이면 다시 생성해 같은 커밋에 담는다: {@value #REGENERATE}
 */
@IntegrationTest
class OpenApiSpecTest {

    static final Path SPEC = Path.of("docs/api/openapi.json");
    static final String REGENERATE = "./gradlew test --tests '*OpenApiSpecTest' -PwriteOpenApi=true";

    @Autowired
    MockMvcTester mvc;

    private MvcTestResult apiDocs() {
        return mvc.get().uri("/v3/api-docs").exchange();
    }

    @Test
    void 생성한_명세가_커밋된_openapi_json과_같다() throws IOException {
        MvcTestResult result = apiDocs();
        assertThat(result).hasStatusOk();
        String generated = normalize(result.getResponse().getContentAsString(StandardCharsets.UTF_8));

        if (Boolean.getBoolean("openapi.write")) {
            Files.writeString(SPEC, generated + "\n", StandardCharsets.UTF_8);
            // 비교하지 않았다는 것이 결과에 보이도록 통과가 아니라 건너뜀으로 끝낸다
            Assumptions.abort("openapi.json을 다시 썼다. 비교하지 않음");
        }
        String message = "openapi.json이 생성한 명세와 다르다. 의도한 변경이면 재생성: " + REGENERATE;
        assertThat(SPEC).as(message).exists();
        assertThat(generated).as(message).isEqualTo(normalize(Files.readString(SPEC, StandardCharsets.UTF_8)));
    }

    @Test
    void 명세의_servers는_상대_경로다() {
        assertThat(apiDocs()).bodyJson().extractingPath("$.servers[0].url").isEqualTo("/");
    }

    @Test
    void 인증_없이_다른_경로는_막힌다() {
        int status =
                mvc.get().uri("/api/v1/student/home").exchange().getResponse().getStatus();
        assertThat(status).isEqualTo(401);
    }

    @Test
    void 줄바꿈만_다른_명세는_같다고_본다() {
        assertThat(normalize("{\r\n}\r\n")).isEqualTo(normalize("{\n}"));
    }

    /** Windows(CRLF)와 CI(LF)의 차이, 끝 공백을 무시한다. */
    static String normalize(String text) {
        return text.replace("\r\n", "\n").stripTrailing();
    }
}
