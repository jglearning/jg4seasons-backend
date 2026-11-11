package com.jglearning.jg4seasons.system.web;

import com.jglearning.jg4seasons.infra.security.Principals;
import com.jglearning.jg4seasons.system.SystemAcademyService;
import com.jglearning.jg4seasons.system.web.dto.AcademyCreateRequest;
import com.jglearning.jg4seasons.system.web.dto.AcademyCreatedResponse;
import com.jglearning.jg4seasons.system.web.dto.AcademyListResponse;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.annotation.security.RolesAllowed;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 7.1·7.2 운영자 학원(O-01·O-02, SYS-07). 운영자만(ADR 0032 — 역할 계층 밖이라 관리자·교사는 403). 운영자는 학원이 없어 JWT에서 사용자만 읽는다.
 * 비활성 운영자의 남은 access는 401.
 */
@RestController
@RequestMapping("/api/v1/system/academies")
@RolesAllowed("SUPERADMIN")
public class SystemAcademyController {

    private final SystemAcademyService service;

    SystemAcademyController(SystemAcademyService service) {
        this.service = service;
    }

    @Operation(summary = "학원 목록", description = "id 순")
    @GetMapping
    public AcademyListResponse listAcademies(@AuthenticationPrincipal Jwt jwt) {
        return service.list(Principals.userId(jwt));
    }

    @Operation(
            summary = "학원 + 첫 관리자 만들기",
            description = "학원 주소(slug)가 있으면 409 ACADEMY_SLUG_TAKEN, 관리자 아이디가 있으면 409 LOGIN_ID_TAKEN — 둘 다 아무것도 남지 않는다."
                    + " licenseEnforced 필수, timezone은 IANA 이름(없으면 Asia/Seoul). X-CSRF 필요")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AcademyCreatedResponse createAcademy(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody AcademyCreateRequest request) {
        return service.create(Principals.userId(jwt), request);
    }
}
