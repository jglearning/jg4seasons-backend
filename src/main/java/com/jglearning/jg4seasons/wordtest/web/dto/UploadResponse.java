package com.jglearning.jg4seasons.wordtest.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.Map;

/** D-1 업로드 주소 */
@Schema(name = "WordTestUploadResponse")
public record UploadResponse(
        @Schema(
                description = "사진을 PUT할 서명 주소(5분). 쿠키를 보내지 않는다(credentials omit)",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String uploadUrl,

        @Schema(
                description = "제출(D-2)에 그대로 보낼 키",
                example = "homework/1/42/0b9e1c2d-3f4a-4b5c-8d6e-7f8091a2b3c4.jpg",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String imageKey,

        @Schema(
                description = "PUT 요청에 그대로 넣을 헤더(서명에 포함 — 다르면 S3가 403)",
                example = "{\"Content-Type\": \"image/jpeg\"}",
                requiredMode = Schema.RequiredMode.REQUIRED)
        Map<String, String> headers,

        @Schema(description = "주소 만료 시각(UTC)", requiredMode = Schema.RequiredMode.REQUIRED)
        Instant expiresAt) {}
