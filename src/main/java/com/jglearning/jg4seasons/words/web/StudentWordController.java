package com.jglearning.jg4seasons.words.web;

import com.jglearning.jg4seasons.infra.web.error.ApiException;
import com.jglearning.jg4seasons.infra.web.error.NotImplemented;
import com.jglearning.jg4seasons.words.web.dto.LessonWordsResponse;
import com.jglearning.jg4seasons.words.web.dto.WordStudyRequest;
import com.jglearning.jg4seasons.words.web.dto.WordStudyResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.annotation.security.RolesAllowed;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** 2.4 단어·2.5 학습 결과(S-06·S-06b). 학생만. 지금은 계약만(이슈 #26): 검증·접근 규칙은 동작하고 본문은 501. */
@RestController
@RolesAllowed("STUDENT")
public class StudentWordController {

    private static final String NOT_YET = " — 구현 전: 501 NOT_IMPLEMENTED";

    @Operation(summary = "회차 단어", description = "지문 순서 → 단어 순서. 열리지 않은 회차는 2.3과 같은 규칙(404·423)" + NOT_YET)
    @GetMapping("/api/v1/student/lessons/{lessonCode}/words")
    public LessonWordsResponse words(
            @Parameter(description = "회차 코드", example = "L2-B1-05")
                    @PathVariable
                    @Pattern(regexp = "^[A-Za-z0-9-]{1,10}$", message = "회차 코드 형식이 아니에요")
                    String lessonCode) {
        throw NotImplemented.yet();
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
