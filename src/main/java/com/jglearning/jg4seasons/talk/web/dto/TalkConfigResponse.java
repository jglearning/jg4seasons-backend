package com.jglearning.jg4seasons.talk.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** 프리토킹 설정 조회(계약 D-1 + 추가 지시 가-1) */
@Schema(name = "TalkConfigResponse")
public record TalkConfigResponse(
        @Schema(
                description = "구독이 있는가. false면 잠금 화면(나머지 칸은 그대로, lessonWords는 빈 배열). 세션 생성은 403 SUBSCRIPTION_REQUIRED",
                requiredMode = Schema.RequiredMode.REQUIRED)
        boolean entitled,

        @Schema(
                description = "학생 반의 레벨",
                allowableValues = {"mint", "green", "blue", "navy", "black"},
                requiredMode = Schema.RequiredMode.REQUIRED)
        String level,

        @Schema(
                description = "한 세션의 시간 제한(초). 이 시간에 마무리 신호를 보내고 연결을 끊는다",
                example = "180",
                requiredMode = Schema.RequiredMode.REQUIRED)
        int maxSeconds,

        @Schema(
                description = "오늘(학원 날짜) 남은 횟수 0~3. 발급에 실패한 세션은 세지 않는다",
                example = "2",
                requiredMode = Schema.RequiredMode.REQUIRED)
        int remainingToday,

        @Schema(description = "주제 카드(순서대로)", requiredMode = Schema.RequiredMode.REQUIRED)
        List<Topic> topics,

        @Schema(
                description = "오늘 회차 단어 전부(교재 순서, 숙어 포함). 캐릭터가 대화에 쓰도록 지시받은 단어와 같은 목록."
                        + " 오늘 회차가 없거나(개강 전) 단어가 없거나 entitled가 false면 빈 배열",
                requiredMode = Schema.RequiredMode.REQUIRED)
        List<LessonWordItem> lessonWords) {

    @Schema(name = "TalkTopic")
    public record Topic(
            @Schema(
                    allowableValues = {"lesson", "day", "likes"},
                    requiredMode = Schema.RequiredMode.REQUIRED)
            String code,

            @Schema(example = "오늘 하루", requiredMode = Schema.RequiredMode.REQUIRED)
            String title) {}

    @Schema(name = "TalkLessonWord")
    public record LessonWordItem(
            @Schema(example = "cheerful", requiredMode = Schema.RequiredMode.REQUIRED)
            String english,

            @Schema(example = "쾌활한", requiredMode = Schema.RequiredMode.REQUIRED)
            String korean) {}
}
