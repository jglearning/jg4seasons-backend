package com.jglearning.jg4seasons.wordtest.web.dto;

import com.jglearning.jg4seasons.wordtest.grading.Section;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** D-2 제출 요청 */
@Schema(name = "WordTestSubmitRequest")
public record SubmitRequest(
        @Schema(
                description = "클라이언트가 만든 UUID(소문자). 같은 값으로 다시 보내면 새로 만들지 않고 기존 제출을 200으로",
                example = "0b9e1c2d-3f4a-4b5c-8d6e-7f8091a2b3c4")
        @NotBlank
        @Pattern(regexp = "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$", message = "UUID 형식이 아니에요")
        String clientSubmissionId,

        @Schema(description = "회차 코드", example = "L2-B1-05")
        @NotBlank
        @Pattern(regexp = "^[A-Za-z0-9-]{1,10}$", message = "회차 코드 형식이 아니에요")
        String lessonCode,

        @Schema(description = "영역. 1단계는 B(철자 쓰기)만 문항이 있다", example = "B") @NotNull
        Section section,

        @Schema(
                description = "업로드 주소 발급(D-1)이 준 키 그대로",
                example = "homework/1/42/0b9e1c2d-3f4a-4b5c-8d6e-7f8091a2b3c4.jpg")
        @NotBlank
        @Size(max = 200)
        String imageKey) {}
