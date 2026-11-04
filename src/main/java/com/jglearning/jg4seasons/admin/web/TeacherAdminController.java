package com.jglearning.jg4seasons.admin.web;

import com.jglearning.jg4seasons.admin.TeacherAdminService;
import com.jglearning.jg4seasons.admin.web.dto.TeacherCreateRequest;
import com.jglearning.jg4seasons.admin.web.dto.TeacherListResponse;
import com.jglearning.jg4seasons.admin.web.dto.TeacherResponse;
import com.jglearning.jg4seasons.admin.web.dto.TeacherUpdateRequest;
import com.jglearning.jg4seasons.infra.security.Principals;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.annotation.security.RolesAllowed;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 5.3 교사 계정(A-03). 관리자만. 학원은 access 쿠키(JWT aca)에서만 — 다른 학원 교사·반 id는 404. */
@RestController
@RequestMapping("/api/v1/admin/teachers")
@RolesAllowed("ADMIN")
public class TeacherAdminController {

    private final TeacherAdminService service;

    TeacherAdminController(TeacherAdminService service) {
        this.service = service;
    }

    @Operation(summary = "교사·관리자 목록", description = "id 순")
    @GetMapping
    public TeacherListResponse list(@AuthenticationPrincipal Jwt jwt) {
        return service.list(Principals.academyId(jwt));
    }

    @Operation(
            summary = "교사 생성",
            description = "아이디가 이미 있으면(전체 학원, 대소문자 무관) 409 LOGIN_ID_TAKEN, 다른 학원 반이 있으면 404. X-CSRF 필요")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TeacherResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody TeacherCreateRequest request) {
        return service.create(Principals.academyId(jwt), request);
    }

    @Operation(
            summary = "교사 변경",
            description = "빠진 칸은 그대로. 자기 비활성화는 409 SELF_CHANGE_NOT_ALLOWED, 다른 학원 교사·반은 404."
                    + " 비활성화·비밀번호 재설정이면 그 교사의 refresh를 모두 끊음. X-CSRF 필요")
    @PatchMapping("/{id}")
    public TeacherResponse update(
            @AuthenticationPrincipal Jwt jwt, @PathVariable long id, @Valid @RequestBody TeacherUpdateRequest request) {
        return service.update(Principals.academyId(jwt), Principals.userId(jwt), id, request);
    }
}
