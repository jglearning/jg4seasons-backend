package com.jglearning.jg4seasons.infra.web;

import com.jglearning.jg4seasons.infra.web.error.ApiException;
import com.jglearning.jg4seasons.infra.web.error.CommonErrorCode;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.time.LocalDate;
import java.util.Map;
import java.util.Objects;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 테스트 전용: 오류 응답 형식을 확인하려고 일부러 실패하는 엔드포인트. openapi.json에서 제외(@Hidden), 운영 jar에 없음. */
@Hidden
@RestController
@RequestMapping("/test-support/errors")
class ErrorProbeController {

    record ProbeRequest(@NotBlank String name, @Min(1) int count) {}

    record JsonProbe(int count, boolean flag, String name) {}

    record ModelProbe(@Min(1) int count) {}

    /** 객체 단위 제약(두 값이 같아야 함) — 칸이 아닌 전체 오류 */
    @SameValues
    record ConsistentProbe(String first, String second) {}

    /** 응답으로 바꾸다 실패하는 객체(게터에서 예외) */
    record Unwritable() {
        public String getValue() {
            throw new IllegalStateException("unwritable-detail-xyz");
        }
    }

    @PostMapping("/validation")
    Map<String, Boolean> validation(@Valid @RequestBody ProbeRequest request) {
        return Map.of("ok", true);
    }

    @PostMapping("/json")
    Map<String, Boolean> json(@RequestBody JsonProbe request) {
        return Map.of("ok", true);
    }

    @GetMapping("/typed/{id}")
    Map<String, Boolean> typed(@PathVariable Long id, @RequestParam LocalDate date, @RequestParam @Min(1) int page) {
        return Map.of("ok", true);
    }

    @GetMapping("/model")
    Map<String, Boolean> model(@Valid @ModelAttribute ModelProbe probe) {
        return Map.of("ok", true);
    }

    @PostMapping("/consistent")
    Map<String, Boolean> consistent(@Valid @RequestBody ConsistentProbe probe) {
        return Map.of("ok", true);
    }

    @GetMapping("/unwritable")
    Unwritable unwritable() {
        return new Unwritable();
    }

    @GetMapping("/api-exception")
    Map<String, Boolean> apiException() {
        throw new ApiException(CommonErrorCode.NOT_FOUND, "회차를 찾을 수 없어요").with("lessonCode", "L2-B1-99");
    }

    @GetMapping("/boom")
    Map<String, Boolean> boom() {
        throw new IllegalStateException("secret-detail-xyz");
    }

    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    @Constraint(validatedBy = SameValuesValidator.class)
    @interface SameValues {
        String message() default "두 값이 같아야 해요";

        Class<?>[] groups() default {};

        Class<? extends Payload>[] payload() default {};
    }

    static class SameValuesValidator implements ConstraintValidator<SameValues, ConsistentProbe> {
        @Override
        public boolean isValid(ConsistentProbe value, ConstraintValidatorContext context) {
            return value == null || Objects.equals(value.first(), value.second());
        }
    }
}
