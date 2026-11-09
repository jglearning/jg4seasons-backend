package com.jglearning.jg4seasons.teacher.web;

import com.jglearning.jg4seasons.infra.security.Principals;
import com.jglearning.jg4seasons.infra.web.error.ApiException;
import com.jglearning.jg4seasons.infra.web.error.NotImplemented;
import com.jglearning.jg4seasons.teacher.TeacherStudentService;
import com.jglearning.jg4seasons.teacher.web.dto.PersonalCodeResponse;
import com.jglearning.jg4seasons.teacher.web.dto.SessionCancelRequest;
import com.jglearning.jg4seasons.teacher.web.dto.SessionListResponse;
import com.jglearning.jg4seasons.teacher.web.dto.SessionUpdateRequest;
import com.jglearning.jg4seasons.teacher.web.dto.StudentDetailResponse;
import com.jglearning.jg4seasons.teacher.web.dto.TeacherLessonDetailResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.annotation.security.RolesAllowed;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 4.3~4.8 교사 학생·회차 상세(T-04·T-05), 진도표(T-06), 개인 코드 재발급. 교사(관리자 포함), 담당 반·그 반 학생만 — 그 밖은 404. 4.8 재발급은 구현(이슈 #29), 나머지는 계약만(이슈 #26).
 */
@RestController
@RolesAllowed("TEACHER")
public class TeacherDetailController {

    private static final String NOT_YET = " — 구현 전: 501 NOT_IMPLEMENTED";

    private final TeacherStudentService students;

    TeacherDetailController(TeacherStudentService students) {
        this.students = students;
    }

    @Operation(summary = "학생 상세", description = "그 반에서 열린 회차(seq 순)별 트랙 청취·단어 학습" + NOT_YET)
    @GetMapping("/api/v1/teacher/classes/{classId}/students/{studentId}")
    public StudentDetailResponse student(@PathVariable @Positive long classId, @PathVariable @Positive long studentId) {
        throw NotImplemented.yet();
    }

    @Operation(summary = "회차 상세", description = "학생 × 트랙 표(선택 속도 포함)" + NOT_YET)
    @GetMapping("/api/v1/teacher/classes/{classId}/lessons/{lessonCode}")
    public TeacherLessonDetailResponse lesson(
            @PathVariable @Positive long classId,
            @Parameter(description = "회차 코드", example = "L2-B1-05")
                    @PathVariable
                    @Pattern(regexp = "^[A-Za-z0-9-]{1,10}$", message = "회차 코드 형식이 아니에요")
                    String lessonCode) {
        throw NotImplemented.yet();
    }

    @Operation(summary = "진도표", description = "휴강 행 포함, seq 순" + NOT_YET)
    @GetMapping("/api/v1/teacher/classes/{classId}/sessions")
    public SessionListResponse sessions(@PathVariable @Positive long classId) {
        throw NotImplemented.yet();
    }

    @Operation(
            summary = "진도표 한 행 수정",
            description = "날짜·메모. shiftFollowing이면 뒤 수업도 같은 간격으로 민다. 바꿀 값이 없으면 400. 회차 순서 바꾸기는 1차에 없다."
                    + " 바뀐 뒤 진도표 전체. X-CSRF 필요" + NOT_YET)
    @PatchMapping("/api/v1/teacher/classes/{classId}/sessions/{seq}")
    public SessionListResponse updateSession(
            @PathVariable @Positive long classId,
            @Parameter(description = "진도표 행 번호 1~99") @PathVariable @Min(1) @Max(99) int seq,
            @Valid @RequestBody SessionUpdateRequest request) {
        if (!request.hasChange()) {
            throw ApiException.validation("body", "바꿀 값이 없어요");
        }
        throw NotImplemented.yet();
    }

    @Operation(
            summary = "휴강",
            description =
                    "그 행은 cancelled로 남고, shiftFollowing이면 그 회차부터 다음 수업일로 밀린다(24회 유지). 바뀐 뒤 진도표 전체. X-CSRF 필요" + NOT_YET)
    @PostMapping("/api/v1/teacher/classes/{classId}/sessions/{seq}/cancel")
    public SessionListResponse cancelSession(
            @PathVariable @Positive long classId,
            @Parameter(description = "진도표 행 번호 1~99") @PathVariable @Min(1) @Max(99) int seq,
            @Valid @RequestBody SessionCancelRequest request) {
        throw NotImplemented.yet();
    }

    @Operation(
            summary = "개인 코드 재발급",
            description = "교사는 담당 반에 지금 소속인 학생만, 관리자는 학원 안 어느 반이든 지금 소속인 학생 — 그 밖(다른 학원·담당 아님·퇴원·반 없음)은 404."
                    + " 새 코드(숫자 4자리, 0으로 시작 가능) 평문은 이 응답에서만(다시 볼 수 없음). 옛 코드는 바로 무효, 그 학생의 refresh는 모두 끊긴다"
                    + "(남은 access는 최대 30분). 감사 로그. 요청한 교사가 비활성화됐으면 401. X-CSRF 필요")
    @PostMapping("/api/v1/teacher/students/{studentId}/personal-code/regenerate")
    public PersonalCodeResponse regeneratePersonalCode(
            @AuthenticationPrincipal Jwt jwt, @PathVariable @Positive long studentId) {
        return students.regeneratePersonalCode(Principals.academyId(jwt), Principals.userId(jwt), studentId);
    }
}
