package com.jglearning.jg4seasons.words.web;

import com.jglearning.jg4seasons.infra.security.Principals;
import com.jglearning.jg4seasons.infra.web.error.ApiException;
import com.jglearning.jg4seasons.infra.web.error.NotImplemented;
import com.jglearning.jg4seasons.words.LessonWordsService;
import com.jglearning.jg4seasons.words.web.dto.LessonWordsResponse;
import com.jglearning.jg4seasons.words.web.dto.WordStudyRequest;
import com.jglearning.jg4seasons.words.web.dto.WordStudyResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.annotation.security.RolesAllowed;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 2.4 단어·2.5 학습 결과(S-06·S-06b). 학생만. 학원·학생은 access 쿠키(JWT)에서만 — 열림 규칙은 2.3과 같다(소속 반 진도표에 없으면 404, 수업일 전 423). 2.5는
 * 아직 계약만(501, 이슈 #37 Task 4).
 */
@RestController
@RolesAllowed("STUDENT")
public class StudentWordController {

    private static final String NOT_YET = " — 구현 전: 501 NOT_IMPLEMENTED";

    private final LessonWordsService words;

    StudentWordController(LessonWordsService words) {
        this.words = words;
    }

    @Operation(
            summary = "회차 단어",
            description = "교재 순서: 어휘책 그 강의 단어 → 숙어 → 어휘책에 없는 회차 어휘. source로 출처(book·extra)를 구분한다."
                    + " 예문 해석·음원이 없는 단어가 있다(null). 단어가 없으면 빈 목록."
                    + " 열림 규칙은 2.3과 같다: 어느 소속 반 진도표에도 없는 회차 404, 수업일 전·휴강만 있는 회차 423 LESSON_LOCKED")
    @GetMapping("/api/v1/student/lessons/{lessonCode}/words")
    public LessonWordsResponse words(
            @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "회차 코드", example = "L2-B1-05")
                    @PathVariable
                    @Pattern(regexp = "^[A-Za-z0-9-]{1,10}$", message = "회차 코드 형식이 아니에요")
                    String lessonCode) {
        return words.words(Principals.academyId(jwt), Principals.userId(jwt), lessonCode);
    }

    @Operation(
            summary = "카드·학습 결과 저장",
            description = "보낼 칸만, 하나도 없으면 400. 저장된 값보다 작은 값은 무시(단조 증가). 화면을 떠날 때·모드 한 바퀴 끝에 1회. X-CSRF 필요" + NOT_YET)
    @PutMapping("/api/v1/student/lessons/{lessonCode}/word-study")
    public WordStudyResponse saveStudy(
            @Parameter(description = "회차 코드", example = "L2-B1-05")
                    @PathVariable
                    @Pattern(regexp = "^[A-Za-z0-9-]{1,10}$", message = "회차 코드 형식이 아니에요")
                    String lessonCode,
            @Valid @RequestBody WordStudyRequest request) {
        if (!request.hasAny()) {
            throw ApiException.validation("body", "저장할 값이 없어요");
        }
        throw NotImplemented.yet();
    }
}
