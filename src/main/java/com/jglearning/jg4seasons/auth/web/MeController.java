package com.jglearning.jg4seasons.auth.web;

import com.jglearning.jg4seasons.auth.web.dto.MeResponse;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.annotation.security.RolesAllowed;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** 1.7 내 정보. 학원은 access 쿠키(JWT aca)에서만 정한다. */
@RestController
public class MeController {

    @Operation(summary = "내 정보", description = "access 쿠키가 없거나 만료면 401 UNAUTHORIZED")
    @RolesAllowed("STUDENT")
    @GetMapping("/api/v1/me")
    public MeResponse me() {
        throw new UnsupportedOperationException("Task 6");
    }
}
