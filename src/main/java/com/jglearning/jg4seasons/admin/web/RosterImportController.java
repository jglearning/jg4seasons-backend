package com.jglearning.jg4seasons.admin.web;

import com.jglearning.jg4seasons.admin.RosterImportService;
import com.jglearning.jg4seasons.admin.web.dto.RosterImportCheckResponse;
import com.jglearning.jg4seasons.admin.web.dto.RosterImportRequest;
import com.jglearning.jg4seasons.infra.security.Principals;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.annotation.security.RolesAllowed;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 5.5 명단 가져오기(A-0x, ADR 0036). 관리자만, 학원은 access 쿠키에서만 — 다른 학원 반·교사는 없는 것과 같다. */
@RestController
@RequestMapping("/api/v1/admin")
@RolesAllowed("ADMIN")
public class RosterImportController {

    private final RosterImportService service;

    RosterImportController(RosterImportService service) {
        this.service = service;
    }

    @Operation(
            summary = "명단 가져오기 검사",
            description = "아무것도 만들지 않는다. 형식(타입·길이·개수: 반 1~30, 반마다 학생 1~50)이 틀리면 400 VALIDATION, 형식이 맞으면 200과 데이터 문제 목록."
                    + " errors가 비어야 만들기가 성공한다(그 사이 다른 관리자가 바꾸지 않았다면). warnings는 만들기를 막지 않는다. X-CSRF 필요")
    @PostMapping("/roster-imports/check")
    public RosterImportCheckResponse checkRosterImport(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody RosterImportRequest request) {
        return service.check(Principals.academyId(jwt), Principals.userId(jwt), request);
    }
}
