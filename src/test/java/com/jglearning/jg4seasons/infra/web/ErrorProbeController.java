package com.jglearning.jg4seasons.infra.web;

import com.jglearning.jg4seasons.infra.web.error.ApiException;
import com.jglearning.jg4seasons.infra.web.error.CommonErrorCode;
import com.jglearning.jg4seasons.infra.web.error.NotImplemented;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.annotation.security.PermitAll;
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
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
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
@PermitAll // 테스트 전용. URL 층에서 인증은 여전히 필요(@WithMockUser), 역할은 보지 않는다
@RestController
@RequestMapping("/test-support/errors")
class ErrorProbeController {

    record ProbeRequest(@NotBlank String name, @Min(1) int count) {}

    record JsonProbe(int count, boolean flag, String name) {}

    record ModelProbe(@Min(1) int count) {}

    enum Kind {
        A,
        B
    }

    record Item(@Min(1) int count) {}

    /** 래퍼 숫자·불리언·실수·enum·날짜·중첩 리스트가 있는 본문. 모든 칸은 선택(빠지면 null) */
    record WideProbe(
            Long id,
            Boolean active,
            BigDecimal ratio,
            Kind kind,
            LocalDate date,
            @Valid List<Item> items,
            String name) {}

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

    @PostMapping("/json-wide")
    Map<String, Boolean> jsonWide(@Valid @RequestBody WideProbe request) {
        return Map.of("ok", true);
    }

    @GetMapping("/typed/{id}")
    Map<String, Boolean> typed(@PathVariable Long id, @RequestParam LocalDate date, @RequestParam @Min(1) int page) {
        return Map.of("ok", true);
    }

    /** 경로 변수 제약(@Min)과 본문 검증(@Valid)이 함께 있으면 Spring이 메서드 검증으로 한꺼번에 처리한다 */
    @PostMapping("/typed/{id}/body")
    Map<String, Boolean> typedWithBody(@PathVariable @Min(1) long id, @Valid @RequestBody ProbeRequest request) {
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

    @GetMapping("/service-validation")
    Map<String, Boolean> serviceValidation() {
        throw ApiException.validation("password", "비밀번호는 72바이트 이하예요");
    }

    @GetMapping("/service-validation-no-field")
    Map<String, Boolean> serviceValidationNoField() {
        throw new ApiException(CommonErrorCode.VALIDATION, "입력이 맞지 않아요");
    }

    @GetMapping("/locked")
    Map<String, Boolean> locked() {
        throw new ApiException(ProbeErrorCode.PROBE_LOCKED, "10분 뒤에 다시 시도해 주세요")
                .with("until", Instant.parse("2026-11-04T01:10:00Z"));
    }

    @GetMapping("/upstream")
    Map<String, Boolean> upstream() {
        throw new ApiException(
                ProbeErrorCode.PROBE_UPSTREAM, "잠시 후 다시 시도해 주세요", new IllegalStateException("cause-xyz"));
    }

    /** 메서드 보안(@PreAuthorize 등)이 컨트롤러 안에서 던지는 거부 예외를 흉내 낸다 */
    @GetMapping("/denied")
    Map<String, Boolean> denied() {
        throw new AccessDeniedException("probe");
    }

    @GetMapping("/unauthenticated")
    Map<String, Boolean> unauthenticated() {
        throw new InsufficientAuthenticationException("probe");
    }

    @GetMapping("/boom")
    Map<String, Boolean> boom() {
        throw new IllegalStateException("secret-detail-xyz");
    }

    /** 우리 코드가 아니라 프레임워크가 낸 501(진짜 서버 쪽 미지원) */
    @GetMapping("/framework-501")
    Map<String, Boolean> framework501() {
        throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.NOT_IMPLEMENTED);
    }

    @GetMapping("/not-implemented")
    Map<String, Boolean> notImplemented() {
        throw NotImplemented.yet();
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
