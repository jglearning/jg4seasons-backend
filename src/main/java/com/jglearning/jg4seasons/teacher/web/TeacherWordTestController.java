package com.jglearning.jg4seasons.teacher.web;

import com.jglearning.jg4seasons.infra.security.Principals;
import com.jglearning.jg4seasons.teacher.TeacherWordTestService;
import com.jglearning.jg4seasons.teacher.web.dto.TeacherWordTestDetail;
import com.jglearning.jg4seasons.teacher.web.dto.TeacherWordTestList;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.security.RolesAllowed;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** D-5·D-6 교사 단어시험 결과 조회(이슈 #74). 교사(관리자 포함), 담당 반·그 반 학생만 — 그 밖은 404. 조회만(정정은 2단계) */
@RestController
@RolesAllowed("TEACHER")
public class TeacherWordTestController {

    private final TeacherWordTestService wordTests;

    TeacherWordTestController(TeacherWordTestService wordTests) {
        this.wordTests = wordTests;
    }

    @Operation(
            summary = "반 학생별 단어시험 최근 제출",
            description = "그 반 현재 소속 학생(이름 순, 매트릭스와 같은 학생)마다 그 회차·영역의 마지막 시도. 낸 적이 없으면 submission null."
                    + " 회차 열림·사용권은 보지 않는다. 기능이 꺼진 학원은 404 FEATURE_DISABLED,"
                    + " 범위 밖 반·그 반 진도표에 없는 회차·시험지 없는 영역은 404 NOT_FOUND. 요청한 교사가 비활성화됐으면 401")
    @GetMapping("/api/v1/teacher/classes/{classId}/word-tests")
    public TeacherWordTestList listClassWordTests(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable @Positive long classId,
            @Parameter(description = "회차 코드", example = "L2-B1-01")
                    @RequestParam
                    @Pattern(regexp = "^[A-Za-z0-9-]{1,10}$", message = "회차 코드 형식이 아니에요")
                    String lessonCode,
            @Parameter(
                            description = "영역(1단계는 B)",
                            example = "B",
                            schema = @Schema(allowableValues = {"A", "B", "C", "D"}))
                    @RequestParam
                    @Pattern(regexp = "^[ABCD]$", message = "영역은 A·B·C·D 중 하나예요")
                    String section) {
        return wordTests.list(Principals.academyId(jwt), Principals.userId(jwt), classId, lessonCode, section);
    }

    @Operation(
            summary = "단어시험 제출 상세",
            description = "답안지 사진 보기 서명 주소(5분)와 문항별 결과(graded일 때만, unclear에도 정답)."
                    + " 교사는 진행 중 담당 반에 지금 소속된 학생의 제출만, 관리자는 학원 학생 전체 — 그 밖·없는 제출은 404 NOT_FOUND."
                    + " 기능이 꺼진 학원은 404 FEATURE_DISABLED. 요청한 교사가 비활성화됐으면 401")
    @GetMapping("/api/v1/teacher/word-tests/{id}")
    public TeacherWordTestDetail getTeacherWordTestSubmission(
            @AuthenticationPrincipal Jwt jwt, @PathVariable @Positive long id) {
        return wordTests.detail(Principals.academyId(jwt), Principals.userId(jwt), id);
    }
}
