package com.jglearning.jg4seasons.admin.web;

import com.jglearning.jg4seasons.admin.web.dto.PersonalCodesRegenerateRequest;
import com.jglearning.jg4seasons.admin.web.dto.PersonalCodesResponse;
import com.jglearning.jg4seasons.admin.web.dto.StudentCreateRequest;
import com.jglearning.jg4seasons.admin.web.dto.StudentCreatedResponse;
import com.jglearning.jg4seasons.admin.web.dto.StudentListResponse;
import com.jglearning.jg4seasons.admin.web.dto.StudentMoveRequest;
import com.jglearning.jg4seasons.admin.web.dto.StudentResponse;
import com.jglearning.jg4seasons.admin.web.dto.StudentSearchRequest;
import com.jglearning.jg4seasons.admin.web.dto.StudentSearchResponse;
import com.jglearning.jg4seasons.admin.web.dto.StudentUpdateRequest;
import com.jglearning.jg4seasons.infra.web.error.ApiException;
import com.jglearning.jg4seasons.infra.web.error.NotImplemented;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.annotation.security.RolesAllowed;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 5.2 학생 관리(A-02). 관리자만, 학원은 access 쿠키에서만 — 다른 학원 반·학생은 404. 지금은 계약만(이슈 #26): 검증·접근 규칙은 동작하고 본문은 501.
 */
@RestController
@RequestMapping("/api/v1/admin")
@RolesAllowed("ADMIN")
public class StudentAdminController {

    private static final String NOT_YET = " — 구현 전: 501 NOT_IMPLEMENTED";

    @Operation(summary = "반 학생 목록", description = "현재 소속 학생(이름 순). 개인 코드 평문 없음" + NOT_YET)
    @GetMapping("/classes/{id}/students")
    public StudentListResponse list(@PathVariable long id) {
        throw NotImplemented.yet();
    }

    @Operation(
            summary = "학생 등록",
            description = "1~50명. 학생마다 개인 코드를 발급해 평문을 이 응답에서만 준다(카드 인쇄용, 다시 볼 수 없음). X-CSRF 필요" + NOT_YET)
    @PostMapping("/classes/{id}/students")
    @ResponseStatus(HttpStatus.CREATED)
    public StudentCreatedResponse create(@PathVariable long id, @Valid @RequestBody StudentCreateRequest request) {
        throw NotImplemented.yet();
    }

    @Operation(
            summary = "개인 코드 여러 명 재발급",
            description = "그 반 학생만. 옛 코드는 바로 못 쓰고 그 학생들의 로그인이 끊긴다. 감사 로그. X-CSRF 필요" + NOT_YET)
    @PostMapping("/classes/{id}/students/personal-codes/regenerate")
    public PersonalCodesResponse regeneratePersonalCodes(
            @PathVariable long id, @Valid @RequestBody PersonalCodesRegenerateRequest request) {
        throw NotImplemented.yet();
    }

    @Operation(
            summary = "학생 변경",
            description = "빠진 칸은 그대로, 학교·학년은 빈 문자열이면 지움. 바꿀 값이 없으면 400. status inactive = 퇴원(기록 보존, 감사 로그)."
                    + " X-CSRF 필요" + NOT_YET)
    @PatchMapping("/students/{id}")
    public StudentResponse update(@PathVariable long id, @Valid @RequestBody StudentUpdateRequest request) {
        if (!request.hasChange()) {
            throw ApiException.validation("body", "바꿀 값이 없어요");
        }
        throw NotImplemented.yet();
    }

    @Operation(summary = "학생 반 이동", description = "지금 반에서 나가고 새 반에 들어간다. 기록은 남는다. 감사 로그. X-CSRF 필요" + NOT_YET)
    @PostMapping("/students/{id}/move")
    public StudentResponse move(@PathVariable long id, @Valid @RequestBody StudentMoveRequest request) {
        throw NotImplemented.yet();
    }

    @Operation(summary = "학생 이름으로 찾기", description = "학원 전체(퇴원 포함). 이름은 본문으로만. X-CSRF 필요" + NOT_YET)
    @PostMapping("/students/search")
    public StudentSearchResponse search(@Valid @RequestBody StudentSearchRequest request) {
        throw NotImplemented.yet();
    }
}
