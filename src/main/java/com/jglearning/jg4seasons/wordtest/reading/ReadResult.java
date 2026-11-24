package com.jglearning.jg4seasons.wordtest.reading;

import java.util.List;

/**
 * 답안지 한 장을 읽은 결과(검증 전). 원문·읽은 글자는 로그에 남기지 않는다.
 *
 * @param model 실제로 응답한 모델(다른 모델로 넘어갔으면 그 이름)
 * @param promptVersion 프롬프트 파일 버전(v1)
 */
public record ReadResult(
        List<ReadItem> items, String model, String promptVersion, long inputTokens, long outputTokens) {}
