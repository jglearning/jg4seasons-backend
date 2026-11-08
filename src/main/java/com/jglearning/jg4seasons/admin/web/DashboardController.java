package com.jglearning.jg4seasons.admin.web;

import com.jglearning.jg4seasons.admin.web.dto.DashboardResponse;
import com.jglearning.jg4seasons.infra.web.error.NotImplemented;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.annotation.security.RolesAllowed;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** 5.4 학원 전체 현황(A-04). 관리자만. 지금은 계약만(이슈 #26). */
@RestController
@RolesAllowed("ADMIN")
public class DashboardController {

    @Operation(summary = "학원 현황", description = "진행 중인 반별 오늘 회차·완료 비율, 오늘 들은 학생 수 — 구현 전: 501 NOT_IMPLEMENTED")
    @GetMapping("/api/v1/admin/dashboard")
    public DashboardResponse dashboard() {
        throw NotImplemented.yet();
    }
}
