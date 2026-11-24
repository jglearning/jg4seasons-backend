package com.jglearning.jg4seasons.wordtest.reading;

import java.util.List;
import java.util.Objects;

/**
 * 답안지 한 장을 읽은 결과(검증 전). 원문·읽은 글자는 로그에 남기지 않는다.
 *
 * @param model 실제로 응답한 모델(다른 모델로 넘어갔으면 그 이름)
 * @param promptVersion 프롬프트 파일 버전(v1)
 */
public record ReadResult(
        List<ReadItem> items, String model, String promptVersion, long inputTokens, long outputTokens) {

    /** items는 그대로 둔다(빈 항목은 ReadValidation이 MALFORMED로 — 여기서 막으면 재시도 판단이 바뀜) */
    public ReadResult {
        Objects.requireNonNull(model, "모델 이름이 없다");
        Objects.requireNonNull(promptVersion, "프롬프트 버전이 없다");
        if (inputTokens < 0 || outputTokens < 0) {
            throw new IllegalArgumentException("토큰 수가 음수다");
        }
    }
}
