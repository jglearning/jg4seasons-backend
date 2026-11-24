package com.jglearning.jg4seasons.wordtest.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** D-1 업로드 주소 요청 */
@Schema(name = "WordTestUploadRequest")
public record UploadRequest(
        @Schema(description = "회차 코드", example = "L2-B1-05")
        @NotBlank
        @Pattern(regexp = "^[A-Za-z0-9-]{1,10}$", message = "회차 코드 형식이 아니에요")
        String lessonCode,

        @Schema(description = "사진 형식. image/jpeg · image/png만(그 밖은 400 INVALID_IMAGE)", example = "image/jpeg")
        @NotBlank
        @Size(max = 100)
        String contentType) {}
