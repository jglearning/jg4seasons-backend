package com.jglearning.jg4seasons.talk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jglearning.jg4seasons.talk.TalkSessionService.Result;
import com.jglearning.jg4seasons.talk.TalkSessionService.Result.Connection;
import com.jglearning.jg4seasons.talk.realtime.IssuedSecret;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/** 세션 생성 결과의 짝: 연결 정보(임시 키 + 마무리 신호)는 created일 때만, 둘이 함께 */
class TalkSessionResultTest {

    static final Connection CONNECTION =
            new Connection(new IssuedSecret("ek_1", Instant.parse("2026-11-26T03:01:00Z"), "m"), "wrap up");

    @Test
    void 새_세션은_created이고_연결_정보가_있다() {
        Result result = Result.created(1, 180, CONNECTION);

        assertThat(result.status()).isEqualTo(TalkSessionStatus.CREATED);
        assertThat(result.created()).isTrue();
        assertThat(result.connection()).isEqualTo(CONNECTION);
    }

    @Test
    void 재발급은_created이고_새_세션이_아니다() {
        Result result = Result.reissued(1, 180, CONNECTION);

        assertThat(result.status()).isEqualTo(TalkSessionStatus.CREATED);
        assertThat(result.created()).isFalse();
        assertThat(result.connection()).isNotNull();
    }

    @Test
    void 끝난_세션은_연결_정보가_없다() {
        Result result = Result.closed(1, TalkSessionStatus.FAILED, 180);

        assertThat(result.status()).isEqualTo(TalkSessionStatus.FAILED);
        assertThat(result.created()).isFalse();
        assertThat(result.connection()).isNull();
    }

    @Test
    void 짝이_어긋나면_만들_수_없다() {
        IssuedSecret secret = CONNECTION.secret();
        assertThatThrownBy(() -> new Connection(secret, null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Connection(null, "wrap up")).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Result(1, TalkSessionStatus.FAILED, 180, CONNECTION, false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Result(1, TalkSessionStatus.CREATED, 180, null, true))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
