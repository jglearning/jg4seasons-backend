package com.jglearning.jg4seasons.admin.web;

import com.jglearning.jg4seasons.admin.DashboardService;
import com.jglearning.jg4seasons.admin.web.dto.DashboardResponse;
import com.jglearning.jg4seasons.infra.security.Principals;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.annotation.security.RolesAllowed;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** 5.4 학원 전체 현황(A-04). 관리자만, 학원은 access 쿠키에서만. 요청한 관리자가 비활성화됐거나 역할이 바뀌었으면(남은 access) 401. */
@RestController
@RolesAllowed("ADMIN")
public class DashboardController {

    private final DashboardService service;

    DashboardController(DashboardService service) {
        this.service = service;
    }

    @Operation(
            summary = "학원 현황",
            description = "진행 중(active) 반 전부(id 순)의 오늘 회차와 그 회차를 완료한 학생 비율(현재 소속 학생 중, 반올림 없음), 오늘 한 번이라도 들은"
                    + " 학생 수. 오늘 = 학원 타임존 날짜(자정 기준), 오늘 들은 학생은 반 소속과 무관하게 그 학원 학생 전부(퇴원 포함)에서 센다")
    @GetMapping("/api/v1/admin/dashboard")
    public DashboardResponse dashboard(@AuthenticationPrincipal Jwt jwt) {
        return service.dashboard(Principals.academyId(jwt), Principals.userId(jwt));
    }
}
