package com.jglearning.jg4seasons.auth.web;

import com.jglearning.jg4seasons.auth.MeService;
import com.jglearning.jg4seasons.auth.web.dto.MeResponse;
import com.jglearning.jg4seasons.infra.security.Principals;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.annotation.security.RolesAllowed;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** 1.7 내 정보. 학원은 access 쿠키(JWT aca)에서만 정한다. */
@RestController
public class MeController {

    private final MeService meService;

    MeController(MeService meService) {
        this.meService = meService;
    }

    @Operation(summary = "내 정보", description = "access 쿠키가 없거나 만료면 401 UNAUTHORIZED. 운영자는 academy null")
    @RolesAllowed({"STUDENT", "TEACHER", "SUPERADMIN"}) // TEACHER는 역할 계층으로 admin 포함. 운영자는 계층 밖(ADR 0032)
    @GetMapping("/api/v1/me")
    public MeResponse me(@AuthenticationPrincipal Jwt jwt) {
        return meService.me(Principals.userId(jwt), Principals.academyIdOrNull(jwt));
    }
}
