package com.jglearning.jg4seasons.wordtest.reading;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** SDK 오류 분류: 다시 해도 같은 요청 거절(400·413·422)·설정 오류(401·403·404)는 재시도하지 않고, 한도·서버 오류만 다시 */
class AnthropicErrorsTest {

    @ParameterizedTest(name = "{0} → {2}")
    @CsvSource({
        "400, BadRequestException, REJECTED",
        "413, ContentTooLargeException, REJECTED",
        "422, UnprocessableEntityException, REJECTED",
        "401, UnauthorizedException, CONFIG",
        "403, PermissionDeniedException, CONFIG",
        "404, NotFoundException, CONFIG",
        "429, RateLimitException, API_ERROR",
        "500, InternalServerException, API_ERROR",
        "529, OverloadedException, API_ERROR",
    })
    void 상태_코드로_실패_종류를_나눈다(int status, String type, ReadFailure.Kind kind) {
        ReadFailure failure = AnthropicAnswerSheetReader.classify(status, type, "req_123");

        assertThat(failure.kind()).isEqualTo(kind);
        assertThat(failure.getMessage()).isEqualTo(kind.name() + ": " + status + " " + type + " req=req_123");
    }

    @org.junit.jupiter.api.Test
    void 거절과_설정_오류는_다시_하지_않는다() {
        assertThat(ReadFailure.Kind.REJECTED.retryable()).isFalse();
        assertThat(ReadFailure.Kind.CONFIG.retryable()).isFalse();
        assertThat(AnthropicAnswerSheetReader.classify(500, "X", null).getMessage())
                .endsWith("req=-");
    }
}
