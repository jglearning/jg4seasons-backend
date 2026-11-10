package com.jglearning.jg4seasons.admin.web;

import com.jglearning.jg4seasons.admin.ClassAdminService;
import com.jglearning.jg4seasons.admin.web.dto.AccessCodeResponse;
import com.jglearning.jg4seasons.admin.web.dto.ClassCreateRequest;
import com.jglearning.jg4seasons.admin.web.dto.ClassListResponse;
import com.jglearning.jg4seasons.admin.web.dto.ClassResponse;
import com.jglearning.jg4seasons.admin.web.dto.ClassUpdateRequest;
import com.jglearning.jg4seasons.infra.security.Principals;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.annotation.security.RolesAllowed;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 5.1 반 관리(A-01). 관리자만. 학원은 access 쿠키(JWT aca)에서만 — 다른 학원 반·교사 id는 404. 요청한 관리자가 비활성화됐거나 역할이 바뀌었거나 학원이
 * 정지됐으면(남은 access) 401.
 */
@RestController
@RequestMapping("/api/v1/admin/classes")
@RolesAllowed("ADMIN")
public class ClassAdminController {

    private final ClassAdminService service;

    ClassAdminController(ClassAdminService service) {
        this.service = service;
    }

    @Operation(summary = "반 목록", description = "id 순, 가입 코드 포함. status를 빼면 전부")
    @GetMapping
    public ClassListResponse list(
            @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "active · finished · archived")
                    @RequestParam(required = false)
                    @Pattern(regexp = "^(active|finished|archived)$", message = "상태는 active·finished·archived예요")
                    String status) {
        return service.list(Principals.academyId(jwt), Principals.userId(jwt), Optional.ofNullable(status));
    }

    @Operation(
            summary = "반 생성",
            description = "진도표(권의 회차 수만큼 — 블랙 28, 그 밖 24회, 개강일 포함, 수업 요일마다, 공휴일 미반영)와 가입 코드를 함께 만든다."
                    + " 없는 권·잘못된 요일·이름은 400(errors[].field), 다른 학원·비활성 교사는 404. X-CSRF 필요")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ClassResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ClassCreateRequest request) {
        return service.create(Principals.academyId(jwt), Principals.userId(jwt), request);
    }

    @Operation(summary = "반 상세", description = "다른 학원 반은 404")
    @GetMapping("/{id}")
    public ClassResponse find(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        return service.find(Principals.academyId(jwt), Principals.userId(jwt), id);
    }

    @Operation(
            summary = "반 변경",
            description = "빠진 칸은 그대로. 권·개강일·요일은 바꿀 수 없음(새 반으로). active가 아니면 그 반 가입 코드로 입장할 수 없음."
                    + " teacherIds는 전체 교체. 다른 학원 반·교사는 404. X-CSRF 필요")
    @PatchMapping("/{id}")
    public ClassResponse update(
            @AuthenticationPrincipal Jwt jwt, @PathVariable long id, @Valid @RequestBody ClassUpdateRequest request) {
        return service.update(Principals.academyId(jwt), Principals.userId(jwt), id, request);
    }

    @Operation(
            summary = "가입 코드 재발급",
            description = "옛 코드로는 새로 입장할 수 없고, 이미 로그인한 학생은 그대로. 감사 로그에 남는다. 다른 학원 반은 404. X-CSRF 필요")
    @PostMapping("/{id}/access-code/regenerate")
    public AccessCodeResponse regenerateAccessCode(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        return service.regenerateAccessCode(Principals.academyId(jwt), Principals.userId(jwt), id);
    }
}
