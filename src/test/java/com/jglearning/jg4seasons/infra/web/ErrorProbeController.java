package com.jglearning.jg4seasons.infra.web;

import com.jglearning.jg4seasons.infra.web.error.ApiException;
import com.jglearning.jg4seasons.infra.web.error.CommonErrorCode;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 테스트 전용: 오류 응답 형식을 확인하려고 일부러 실패하는 엔드포인트. openapi.json에서 제외(@Hidden), 운영 jar에 없음. */
@Hidden
@RestController
@RequestMapping("/test-support/errors")
class ErrorProbeController {

    record ProbeRequest(@NotBlank String name, @Min(1) int count) {}

    record JsonProbe(int count, boolean flag, String name) {}

    @PostMapping("/validation")
    Map<String, Boolean> validation(@Valid @RequestBody ProbeRequest request) {
        return Map.of("ok", true);
    }

    @PostMapping("/json")
    Map<String, Boolean> json(@RequestBody JsonProbe request) {
        return Map.of("ok", true);
    }

    @GetMapping("/api-exception")
    Map<String, Boolean> apiException() {
        throw new ApiException(CommonErrorCode.NOT_FOUND, "회차를 찾을 수 없어요").with("lessonCode", "L2-B1-99");
    }

    @GetMapping("/boom")
    Map<String, Boolean> boom() {
        throw new IllegalStateException("secret-detail-xyz");
    }
}
