package com.jglearning.jg4seasons.infra.openapi;

import static org.assertj.core.api.Assertions.assertThat;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.junit.jupiter.api.Test;

/** 모든 API에 오류 응답(default → application/problem+json, Problem)이 붙는다. 아직 공개 API가 없어 가짜 명세로 확인한다. */
class ProblemResponsesCustomizerTest {

    @Test
    void 모든_API에_오류_응답이_붙는다() {
        OpenAPI openApi = new OpenAPI()
                .paths(new Paths()
                        .addPathItem(
                                "/api/v1/sample",
                                new PathItem()
                                        .get(new Operation()
                                                .responses(new ApiResponses()
                                                        .addApiResponse("200", new ApiResponse().description("ok"))))
                                        .post(new Operation()
                                                .responses(new ApiResponses()
                                                        .addApiResponse("201", new ApiResponse().description("ok"))))));

        new OpenApiConfig().problemResponses().customise(openApi);

        PathItem item = openApi.getPaths().get("/api/v1/sample");
        for (Operation operation : new Operation[] {item.getGet(), item.getPost()}) {
            ApiResponse error = operation.getResponses().get("default");
            assertThat(error).isNotNull();
            assertThat(error.getContent()
                            .get("application/problem+json")
                            .getSchema()
                            .get$ref())
                    .isEqualTo("#/components/schemas/Problem");
        }
        assertThat(openApi.getComponents().getSchemas()).containsKey("Problem");
    }

    @Test
    void 잠금_해제_시각_until은_선택_필드인_날짜_시각이다() {
        Schema<?> problem = OpenApiConfig.problemSchema();

        Schema<?> until = (Schema<?>) problem.getProperties().get("until");
        assertThat(until).isNotNull();
        assertThat(until.getType()).isEqualTo("string");
        assertThat(until.getFormat()).isEqualTo("date-time");
        assertThat(problem.getRequired()).doesNotContain("until");
    }
}
