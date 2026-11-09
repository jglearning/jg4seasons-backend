package com.jglearning.jg4seasons.teacher.web;

import com.jglearning.jg4seasons.infra.security.Principals;
import com.jglearning.jg4seasons.infra.web.error.ApiException;
import com.jglearning.jg4seasons.teacher.TeacherClassService;
import com.jglearning.jg4seasons.teacher.web.dto.MatrixResponse;
import com.jglearning.jg4seasons.teacher.web.dto.TeacherClassListResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.annotation.security.RolesAllowed;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 4.1 담당 반 목록·4.2 청취 현황 매트릭스(T-02·T-03). 교사(관리자 포함). 교사는 담당 반만, 관리자는 학원 전체 — 그 밖의 반은 404(범위는 DB의 지금 역할로). 요청한 교사가 비활성화됐으면 401. 이슈 #31.
 */
@RestController
@RolesAllowed("TEACHER")
public class TeacherClassController {

    private final TeacherClassService service;

    TeacherClassController(TeacherClassService service) {
        this.service = service;
    }

    @Operation(
            summary = "담당 반 목록",
            description = "진행 중(active) 반, id 순 — 교사는 담당 반, 관리자는 학원 전체. 오늘 회차(학원 날짜, 학생 화면과 같은 규칙)와 그 회차의"
                    + " 완료·진행 중·시작 전 학생 수(현재 소속), 밀린 수업이 있는 학생 수, 다음 수업, 로그인 경고(최근 1시간 반 전체 개인 코드"
                    + " 실패 20회 이상 — since는 그중 가장 오래된 실패 시각)")
    @GetMapping("/api/v1/teacher/classes")
    public TeacherClassListResponse classes(@AuthenticationPrincipal Jwt jwt) {
        return service.classes(Principals.academyId(jwt), Principals.userId(jwt));
    }

    @Operation(
            summary = "청취 현황 매트릭스",
            description = "학생(현재 소속, 이름 순) × 회차(seq 순). fromSeq·toSeq는 둘 다 보내거나 둘 다 뺀다(빼면 오늘 회차가 든 8개 묶음 1~8·9~16…,"
                    + " 개강 전이면 1~8). 휴강 행은 열로 오지 않는다. 칸은 열린 회차만 학생마다 항상(진도 없으면 not_started, requiredTotal = 지문 수)."
                    + " 교사는 담당 반, 관리자는 학원 반 전부(끝난 반 포함) — 그 밖은 404")
    @GetMapping("/api/v1/teacher/classes/{classId}/matrix")
    public MatrixResponse matrix(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable @Positive long classId,
            @Parameter(description = "시작 seq(포함) 1~99") @RequestParam(required = false) @Min(1) @Max(99)
                    Integer fromSeq,
            @Parameter(description = "끝 seq(포함) 1~99") @RequestParam(required = false) @Min(1) @Max(99) Integer toSeq) {
        if (fromSeq != null && toSeq == null) {
            throw ApiException.validation("toSeq", "fromSeq와 함께 보내 주세요");
        }
        if (fromSeq == null && toSeq != null) {
            throw ApiException.validation("fromSeq", "toSeq와 함께 보내 주세요");
        }
        if (fromSeq != null && fromSeq > toSeq) {
            throw ApiException.validation("fromSeq", "fromSeq는 toSeq보다 클 수 없어요");
        }
        return service.matrix(Principals.academyId(jwt), Principals.userId(jwt), classId, fromSeq, toSeq);
    }
}
