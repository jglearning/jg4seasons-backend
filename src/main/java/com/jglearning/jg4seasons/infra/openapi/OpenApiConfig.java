package com.jglearning.jg4seasons.infra.openapi;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import java.util.Map;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * docs/api/openapi.json의 머리말과 공통 오류 응답.
 *
 * <p>servers는 상대 경로("/")로 둔다. 요청 호스트(localhost 등)가 커밋되는 명세에 들어가지 않게 하려는 것이다. FE·API는 같은 도메인이다(ADR-18).
 *
 * <p>오류 응답은 모든 API에 `default` → `application/problem+json` {@code Problem}으로 싣는다(API 명세 §0.4). springdoc이 예외 처리기에서
 * 응답을 추론하지 않게 `springdoc.override-with-generic-response: false`.
 */
@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    static final String PROBLEM = "Problem";
    static final String PROBLEM_JSON = "application/problem+json";

    @Bean
    public OpenAPI openApi() {
        return new OpenAPI()
                .info(new Info().title("jg4seasons API").version("v1"))
                .servers(List.of(new Server().url("/")));
    }

    @Bean
    public OpenApiCustomizer problemResponses() {
        return openApi -> {
            if (openApi.getComponents() == null) {
                openApi.setComponents(new Components());
            }
            openApi.getComponents().addSchemas(PROBLEM, problemSchema());
            if (openApi.getPaths() == null) {
                return;
            }
            openApi.getPaths().values().forEach(item -> item.readOperations().forEach(OpenApiConfig::addProblem));
        };
    }

    /**
     * null 가능한 객체 칸(@Schema(nullable = true) + 객체 타입)을 {@code anyOf: [{$ref}, {type: null}]}로 바꾼다. springdoc(OAS 3.1)은
     * {@code {"type": "null", "$ref": …}}를 내는데, 3.1에서 $ref 옆의 키는 함께 적용되어 "null이면서 그 객체"가 되고 타입 생성기가 null로만 읽는다.
     */
    @Bean
    public OpenApiCustomizer nullableRefs() {
        return openApi -> {
            if (openApi.getComponents() == null || openApi.getComponents().getSchemas() == null) {
                return;
            }
            openApi.getComponents().getSchemas().values().forEach(OpenApiConfig::fixNullableRefs);
        };
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    static void fixNullableRefs(Schema schema) {
        Map<String, Schema> properties = schema.getProperties();
        if (properties == null) {
            return;
        }
        properties.replaceAll((name, property) -> {
            if (property.get$ref() == null || !isNullable(property)) {
                return property;
            }
            Schema<?> ref = new Schema<>().$ref(property.get$ref());
            Schema<?> nullType = new Schema<>();
            nullType.addType("null");
            Schema<?> replaced = new Schema<>().anyOf(List.of(ref, nullType));
            replaced.setDescription(property.getDescription());
            return replaced;
        });
    }

    @SuppressWarnings("rawtypes")
    private static boolean isNullable(Schema property) {
        return "null".equals(property.getType())
                || (property.getTypes() != null && property.getTypes().contains("null"))
                || Boolean.TRUE.equals(property.getNullable());
    }

    private static void addProblem(Operation operation) {
        if (operation.getResponses() == null) {
            operation.setResponses(new ApiResponses());
        }
        operation
                .getResponses()
                .addApiResponse(
                        "default",
                        new ApiResponse()
                                .description("오류(Problem Details). 프론트는 code로 분기하고 title·detail을 보여 준다")
                                .content(new Content()
                                        .addMediaType(
                                                PROBLEM_JSON,
                                                new MediaType()
                                                        .schema(new Schema<>()
                                                                .$ref("#/components/schemas/" + PROBLEM)))));
    }

    @SuppressWarnings("rawtypes")
    static Schema problemSchema() {
        Schema<?> fieldError = new ObjectSchema()
                .addProperty("field", new StringSchema().description("문제 칸 이름. JSON 문법 오류는 body"))
                .addProperty("message", new StringSchema().description("사용자에게 보여 줄 한국어 문구"))
                .required(List.of("field", "message"));
        return new ObjectSchema()
                .description("RFC 9457 Problem Details + 확장 code·requestId")
                .addProperty("type", new StringSchema().description("/problems/{code 소문자-하이픈}"))
                .addProperty("title", new StringSchema().description("사용자에게 보여 줄 제목(한국어)"))
                .addProperty("status", new IntegerSchema().description("HTTP 상태"))
                .addProperty("detail", new StringSchema().description("사용자에게 보여 줄 설명(한국어)"))
                .addProperty("instance", new StringSchema().description("요청 경로"))
                .addProperty("code", new StringSchema().description("분기용 오류 code(예: VALIDATION, INVALID_CREDENTIALS)"))
                .addProperty("requestId", new StringSchema().description("응답 헤더 X-Request-Id와 같은 값. 로그 대조용"))
                .addProperty("errors", new ArraySchema().items(fieldError).description("400 VALIDATION일 때만. 칸별 오류"))
                .addProperty(
                        "until", new StringSchema().format("date-time").description("423 LOCKED일 때만. 잠금 해제 시각(UTC)"))
                .required(List.of("title", "status", "code", "requestId"));
    }
}
