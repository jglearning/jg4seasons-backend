package com.jglearning.jg4seasons.infra.web.json;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.cfg.CoercionAction;
import tools.jackson.databind.cfg.CoercionInputShape;
import tools.jackson.databind.type.LogicalType;

/**
 * 요청 JSON은 명세 타입 그대로만 받는다(CLAUDE.md §9.4). Jackson 3 기본값은 느슨해서 직접 켠다.
 *
 * <ul>
 *   <li>명세에 없는 필드 거부 — Jackson 3는 FAIL_ON_UNKNOWN_PROPERTIES가 기본 꺼짐(2.x와 반대)
 *   <li>숫자·불리언 칸에 문자열("5", "true") 거부, 정수 칸에 실수(5.5) 거부, 불리언 칸에 숫자 거부
 *   <li>JSON 뒤의 쓰레기 값 거부는 Jackson 3 기본값(FAIL_ON_TRAILING_TOKENS)
 * </ul>
 *
 * 날짜·enum은 문자열이 원래 형식이라 대상이 아니다.
 */
@Configuration(proxyBeanMethods = false)
public class StrictJsonConfig {

    @Bean
    public JsonMapperBuilderCustomizer strictJson() {
        return builder -> builder.enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .withCoercionConfig(
                        LogicalType.Integer,
                        config -> config.setCoercion(CoercionInputShape.String, CoercionAction.Fail)
                                .setCoercion(CoercionInputShape.Float, CoercionAction.Fail))
                .withCoercionConfig(
                        LogicalType.Float, config -> config.setCoercion(CoercionInputShape.String, CoercionAction.Fail))
                .withCoercionConfig(
                        LogicalType.Boolean,
                        config -> config.setCoercion(CoercionInputShape.String, CoercionAction.Fail)
                                .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail));
    }
}
