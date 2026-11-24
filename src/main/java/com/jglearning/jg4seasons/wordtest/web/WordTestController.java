package com.jglearning.jg4seasons.wordtest.web;

import com.jglearning.jg4seasons.infra.security.Principals;
import com.jglearning.jg4seasons.wordtest.SubmissionQueryService;
import com.jglearning.jg4seasons.wordtest.SubmissionService;
import com.jglearning.jg4seasons.wordtest.UploadService;
import com.jglearning.jg4seasons.wordtest.grading.Section;
import com.jglearning.jg4seasons.wordtest.web.dto.SubmissionResponse;
import com.jglearning.jg4seasons.wordtest.web.dto.SubmitRequest;
import com.jglearning.jg4seasons.wordtest.web.dto.SubmitResponse;
import com.jglearning.jg4seasons.wordtest.web.dto.UploadRequest;
import com.jglearning.jg4seasons.wordtest.web.dto.UploadResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.annotation.security.RolesAllowed;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 단어시험 사진 채점 학생 API(S-, 작업 지시 §D). 학생만. 학원·학생은 access 쿠키(JWT)에서만. 그 학원에서 기능이 꺼져 있으면 모두 404 FEATURE_DISABLED. 쓰기는
 * X-CSRF 필요
 */
@RestController
@RolesAllowed("STUDENT")
public class WordTestController {

    private final UploadService uploads;
    private final SubmissionService submissions;
    private final SubmissionQueryService queries;

    WordTestController(UploadService uploads, SubmissionService submissions, SubmissionQueryService queries) {
        this.uploads = uploads;
        this.submissions = submissions;
        this.queries = queries;
    }

    @Operation(
            summary = "단어시험 사진 업로드 주소",
            description = "S3에 바로 PUT할 서명 주소(5분)와 제출에 쓸 imageKey. PUT에는 headers를 그대로 넣고 쿠키는 보내지 않는다."
                    + " JPEG·PNG만(그 밖 400 INVALID_IMAGE). 크기(5MB)는 제출 때 확인한다."
                    + " 기능 꺼짐 404 FEATURE_DISABLED, 없는 회차·진도표에 없는 회차 404 NOT_FOUND, 사용 기간 아님 403 LICENSE_INACTIVE,"
                    + " 수업일 전 423 LESSON_LOCKED")
    @PostMapping("/api/v1/word-tests/uploads")
    public UploadResponse issueWordTestUpload(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody UploadRequest request) {
        return uploads.issue(
                Principals.academyId(jwt), Principals.userId(jwt), request.lessonCode(), request.contentType());
    }

    @Operation(
            summary = "단어시험 사진 제출",
            description = "업로드한 사진을 회차·영역의 답안지로 제출한다. 제출과 채점 작업을 함께 만들고 바로 돌아간다(채점 결과는 제출 조회를 2초 간격으로)."
                    + " 같은 clientSubmissionId를 다시 보내면 새로 만들지 않고 기존 제출을 200으로(다른 학생이 쓴 값이면 400 VALIDATION)."
                    + " 남의 imageKey·올라가지 않은 사진·5MB 초과·JPEG·PNG 아님 400 INVALID_IMAGE, 그 회차·영역 시험지가 없으면 404 NOT_FOUND,"
                    + " 학생·회차·영역별 하루(학원 날짜) 3회 넘으면 429 DAILY_LIMIT. 기능 꺼짐 404 FEATURE_DISABLED, 회차 검사는 업로드 주소와 같다")
    @ApiResponses({
        @ApiResponse(responseCode = "202", description = "새 제출(status pending)"),
        @ApiResponse(responseCode = "200", description = "같은 clientSubmissionId의 기존 제출")
    })
    @PostMapping("/api/v1/word-tests/submissions")
    public ResponseEntity<SubmitResponse> submitWordTest(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody SubmitRequest request) {
        SubmissionService.Result result = submissions.submit(
                Principals.academyId(jwt),
                Principals.userId(jwt),
                request.clientSubmissionId(),
                request.lessonCode(),
                request.section(),
                request.imageKey());
        return ResponseEntity.status(result.created() ? HttpStatus.ACCEPTED : HttpStatus.OK)
                .body(new SubmitResponse(result.id(), result.status().dbValue()));
    }

    @Operation(
            summary = "단어시험 제출 조회",
            description =
                    "본인 제출만(다른 학생·다른 학원·없는 제출은 404 NOT_FOUND). graded가 아니면 score·gradedAt은 null이고 items 칸이 없다."
                            + " items[].result의 unclear는 글자가 흐려 판단 못 한 문항(score에 들어가지 않고 total에는 들어감). 기능 꺼짐 404 FEATURE_DISABLED")
    @GetMapping("/api/v1/word-tests/submissions/{id}")
    public SubmissionResponse getWordTestSubmission(
            @AuthenticationPrincipal Jwt jwt, @Parameter(description = "제출 id") @PathVariable @Positive long id) {
        return queries.get(Principals.academyId(jwt), Principals.userId(jwt), id);
    }

    @Operation(
            summary = "회차·영역의 내 최근 제출",
            description = "그 회차·영역의 마지막 시도. 없으면 204. 모양은 제출 조회와 같다. 그 회차·영역 시험지가 없으면 404 NOT_FOUND,"
                    + " 기능 꺼짐 404 FEATURE_DISABLED(진입 버튼을 숨길 신호), 회차 검사는 업로드 주소와 같다")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "최근 제출"),
        @ApiResponse(responseCode = "204", description = "제출한 적 없음")
    })
    @GetMapping("/api/v1/word-tests/lessons/{lessonCode}/latest")
    public ResponseEntity<SubmissionResponse> getLatestWordTest(
            @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "회차 코드", example = "L2-B1-05")
                    @PathVariable
                    @Pattern(regexp = "^[A-Za-z0-9-]{1,10}$", message = "회차 코드 형식이 아니에요")
                    String lessonCode,
            @Parameter(description = "영역(1단계는 B)", example = "B") @RequestParam Section section) {
        return queries.latest(Principals.academyId(jwt), Principals.userId(jwt), lessonCode, section)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }
}
