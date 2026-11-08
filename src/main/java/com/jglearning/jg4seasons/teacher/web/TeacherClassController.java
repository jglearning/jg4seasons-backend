package com.jglearning.jg4seasons.teacher.web;

import com.jglearning.jg4seasons.infra.web.error.ApiException;
import com.jglearning.jg4seasons.infra.web.error.NotImplemented;
import com.jglearning.jg4seasons.teacher.web.dto.MatrixResponse;
import com.jglearning.jg4seasons.teacher.web.dto.TeacherClassListResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.annotation.security.RolesAllowed;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 4.1 담당 반 목록·4.2 청취 현황 매트릭스(T-02·T-03). 교사(관리자 포함). 교사는 담당 반만, 관리자는 학원 전체 — 그 밖의 반은 404. 지금은 계약만(이슈 #26).
 */
@RestController
@RolesAllowed("TEACHER")
public class TeacherClassController {

    private static final String NOT_YET = " — 구현 전: 501 NOT_IMPLEMENTED";

    @Operation(summary = "담당 반 목록", description = "진행 중인 반. 오늘 회차·완료 현황·밀린 학생 수·로그인 경고" + NOT_YET)
    @GetMapping("/api/v1/teacher/classes")
    public TeacherClassListResponse classes() {
        throw NotImplemented.yet();
    }

    @Operation(
            summary = "청취 현황 매트릭스",
            description = "학생 × 회차. fromSeq·toSeq는 둘 다 보내거나 둘 다 뺀다(빼면 오늘 회차가 든 8개 묶음). 휴강 행은 열로 오지 않는다" + NOT_YET)
    @GetMapping("/api/v1/teacher/classes/{classId}/matrix")
    public MatrixResponse matrix(
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
        throw NotImplemented.yet();
    }
}
