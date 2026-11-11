package com.jglearning.jg4seasons.admin.web;

import com.jglearning.jg4seasons.admin.LicenseAdminService;
import com.jglearning.jg4seasons.admin.web.dto.LicenseListResponse;
import com.jglearning.jg4seasons.infra.security.Principals;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.annotation.security.RolesAllowed;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** 7.5 교재 사용권 보기(A-05). 관리자만, 학원은 access 쿠키에서만. 보기만 — 지정·변경은 운영자(7.4). 비활성 관리자의 남은 access는 401. */
@RestController
@RolesAllowed("ADMIN")
public class LicenseAdminController {

    private final LicenseAdminService service;

    LicenseAdminController(LicenseAdminService service) {
        this.service = service;
    }

    @Operation(
            summary = "교재 사용권 보기",
            description = "자기 학원의 권별 사용 기간·상태·남은 일수·학생 수(사용/한도). 사용권 줄이 있는 권만, 레벨·권 순. today·daysLeft는 학원 타임존 날짜 기준")
    @GetMapping("/api/v1/admin/licenses")
    public LicenseListResponse licenses(@AuthenticationPrincipal Jwt jwt) {
        return service.licenses(Principals.academyId(jwt), Principals.userId(jwt));
    }
}
