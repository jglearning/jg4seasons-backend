package com.jglearning.jg4seasons.infra.security;

import io.swagger.v3.oas.annotations.Hidden;
import jakarta.annotation.security.RolesAllowed;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 테스트 전용: 쿠키 인증·CSRF·역할 확인용 엔드포인트. openapi.json에서 제외(@Hidden), 운영 jar에 없음. */
@Hidden
@RestController
@RequestMapping("/test-support/security")
class SecurityProbeController {

    @RolesAllowed("STUDENT")
    @PostMapping("/student-write")
    Map<String, Boolean> studentWrite() {
        return Map.of("ok", true);
    }

    @RolesAllowed("ADMIN")
    @GetMapping("/admin-only")
    Map<String, Boolean> adminOnly() {
        return Map.of("ok", true);
    }

    @RolesAllowed("TEACHER")
    @GetMapping("/teacher-only")
    Map<String, Boolean> teacherOnly() {
        return Map.of("ok", true);
    }
}
