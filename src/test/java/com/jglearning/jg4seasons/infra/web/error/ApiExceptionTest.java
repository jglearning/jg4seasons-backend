package com.jglearning.jg4seasons.infra.web.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** ApiException: 확장 필드는 예약어를 덮어쓰지 못하고, 예외 메시지(로그에 찍힘)에는 사용자 문구가 들어가지 않는다. */
class ApiExceptionTest {

    @ParameterizedTest
    @ValueSource(strings = {"code", "requestId", "errors", "type", "title", "status", "detail", "instance"})
    void 예약어_키는_with에서_거부한다(String key) {
        ApiException ex = new ApiException(CommonErrorCode.NOT_FOUND, "없어요");
        assertThatThrownBy(() -> ex.with(key, "x")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 확장_필드_값이_null이면_거부한다() {
        ApiException ex = new ApiException(CommonErrorCode.NOT_FOUND, "없어요");
        assertThatThrownBy(() -> ex.with("until", null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 예외_메시지에는_code만_있고_사용자_문구는_없다() {
        ApiException ex = new ApiException(CommonErrorCode.NOT_FOUND, "secret-1234");
        assertThat(ex.getMessage()).isEqualTo("NOT_FOUND").doesNotContain("secret-1234");
    }

    @Test
    void 문구_없이_만들면_detail은_null이다() {
        assertThat(new ApiException(CommonErrorCode.NOT_FOUND).detail()).isNull();
    }

    @Test
    void 원인_예외를_담는다() {
        RuntimeException cause = new RuntimeException("cause");
        assertThat(new ApiException(CommonErrorCode.INTERNAL, null, cause).getCause())
                .isSameAs(cause);
    }
}
