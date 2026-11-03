package com.jglearning.jg4seasons.infra.web.json;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.cfg.CoercionAction;
import tools.jackson.databind.cfg.CoercionInputShape;
import tools.jackson.databind.cfg.EnumFeature;
import tools.jackson.databind.cfg.MutableCoercionConfig;
import tools.jackson.databind.type.LogicalType;

/**
 * 요청 JSON은 명세 타입 그대로만 받는다. Jackson 3 기본값은 느슨해서 직접 켠다.
 *
 * <ul>
 *   <li>명세에 없는 필드 거부 — Jackson 3는 FAIL_ON_UNKNOWN_PROPERTIES가 기본 꺼짐(2.x와 반대)
 *   <li>숫자·불리언 칸: 문자열("5", "true"), 빈 문자열·공백(기본은 조용히 null) 거부. 정수 칸의 실수, 불리언 칸의 숫자 거부
 *   <li>문자열 칸: 숫자·불리언(기본은 "123"·"true"로 바꿔 받음) 거부
 *   <li>enum 칸: 숫자(기본은 순서로 받음) 거부
 *   <li>JSON 뒤의 쓰레기 값 거부는 Jackson 3 기본값(FAIL_ON_TRAILING_TOKENS)
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
public class StrictJsonConfig {

    @Bean
    public JsonMapperBuilderCustomizer strictJson() {
        return builder -> builder.enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(EnumFeature.FAIL_ON_NUMBERS_FOR_ENUMS)
                .withCoercionConfig(
                        LogicalType.Integer,
                        config -> rejectTextual(config).setCoercion(CoercionInputShape.Float, CoercionAction.Fail))
                .withCoercionConfig(LogicalType.Float, StrictJsonConfig::rejectTextual)
                .withCoercionConfig(
                        LogicalType.Boolean,
                        config -> rejectTextual(config).setCoercion(CoercionInputShape.Integer, CoercionAction.Fail))
                .withCoercionConfig(
                        LogicalType.Textual,
                        config -> config.setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                                .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                                .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail));
    }

    /** 문자열·빈 문자열·공백 문자열 입력을 모두 거부(공백을 빈 문자열로 보지 않게 해 문자열 규칙으로 거부) */
    private static MutableCoercionConfig rejectTextual(MutableCoercionConfig config) {
        return config.setCoercion(CoercionInputShape.String, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.EmptyString, CoercionAction.Fail)
                .setAcceptBlankAsEmpty(false);
    }
}
