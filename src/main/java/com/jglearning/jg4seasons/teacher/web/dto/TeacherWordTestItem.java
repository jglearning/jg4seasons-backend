package com.jglearning.jg4seasons.teacher.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** 교사 화면의 문항 결과. 학생 응답과 달리 unclear 문항에도 정답(사진을 보고 판단) */
public record TeacherWordTestItem(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "책의 문항 번호")
        int no,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "문제로 준 쪽(B는 우리말 뜻)", example = "아름다운")
        String prompt,

        @Schema(
                requiredMode = Schema.RequiredMode.REQUIRED,
                description = "서버가 읽은 글자. 빈칸·못 읽음이면 \"\"",
                example = "beautful")
        String written,

        @Schema(
                requiredMode = Schema.RequiredMode.REQUIRED,
                description = "correct·wrong·blank·unclear. unclear = 글자가 흐려 판정 못 함(score에 들어가지 않음, 화면은 \"확인 필요\")",
                allowableValues = {"correct", "wrong", "blank", "unclear"})
        String result,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "정답(unclear에도)", example = "beautiful")
        String answer) {}
