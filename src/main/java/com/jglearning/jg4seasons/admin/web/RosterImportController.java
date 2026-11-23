package com.jglearning.jg4seasons.admin.web;

import com.jglearning.jg4seasons.admin.RosterImportService;
import com.jglearning.jg4seasons.admin.web.dto.RosterImportCheckResponse;
import com.jglearning.jg4seasons.admin.web.dto.RosterImportRequest;
import com.jglearning.jg4seasons.admin.web.dto.RosterImportResponse;
import com.jglearning.jg4seasons.infra.security.Principals;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.annotation.security.RolesAllowed;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
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

    @Operation(
            summary = "명단 가져오기",
            description = "반 항목 순서대로 새 반(진도표·가입 코드·담당 교사)을 만들거나 기존 반을 쓰고, 학생을 새로 만들어(이미 있는 학생을 찾아 옮기지 않음) 넣는다. 한"
                    + " 트랜잭션 — 검사 오류가 하나라도 있으면 첫 오류로 4xx(400 VALIDATION·404·409 CLASS_NOT_ACTIVE·403 LICENSE_INACTIVE·409"
                    + " LICENSE_SEATS_FULL)이고 아무것도 만들지 않는다. 경고는 막지 않는다. 개인 코드 평문은 이 응답에서만(카드 인쇄용). 감사 로그 한 줄."
                    + " X-CSRF 필요")
    @PostMapping("/roster-imports")
    @ResponseStatus(HttpStatus.CREATED)
    public RosterImportResponse createRosterImport(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody RosterImportRequest request) {
        return service.create(Principals.academyId(jwt), Principals.userId(jwt), request);
    }
}
