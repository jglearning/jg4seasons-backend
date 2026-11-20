package com.jglearning.jg4seasons.auth.web;

import com.jglearning.jg4seasons.auth.StudentSettingsService;
import com.jglearning.jg4seasons.auth.web.dto.StudentSettingsPatch;
import com.jglearning.jg4seasons.auth.web.dto.StudentSettingsResponse;
import com.jglearning.jg4seasons.infra.security.Principals;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.annotation.security.RolesAllowed;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** 1.8·1.9 학생 설정(ADR 0035). 학생만 쓴다 — 교사·관리자·운영자는 403. */
@RestController
public class MeSettingsController {

    private final StudentSettingsService service;

    MeSettingsController(StudentSettingsService service) {
        this.service = service;
    }

    @Operation(summary = "내 설정", description = "저장한 적이 없으면 모든 값이 null. 기본값은 화면이 정한다")
    @RolesAllowed("STUDENT")
    @GetMapping("/api/v1/me/settings")
    public StudentSettingsResponse get(@AuthenticationPrincipal Jwt jwt) {
        return service.get(Principals.userId(jwt), Principals.academyIdOrNull(jwt));
    }

    @Operation(
            summary = "내 설정 부분 수정",
            description = "보낸 칸만 바꾼다. 보내지 않은 칸은 그대로, null은 그 칸만 비운다. 응답은 설정 전체."
                    + " 네 목소리 밖의 값은 400 VALIDATION(errors[].field = voice)")
    @RolesAllowed("STUDENT")
    @PatchMapping("/api/v1/me/settings")
    public StudentSettingsResponse patch(@AuthenticationPrincipal Jwt jwt, @RequestBody StudentSettingsPatch patch) {
        return service.patch(Principals.userId(jwt), Principals.academyIdOrNull(jwt), patch);
    }
}
