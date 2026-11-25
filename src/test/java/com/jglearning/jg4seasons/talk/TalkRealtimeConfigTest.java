package com.jglearning.jg4seasons.talk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jglearning.jg4seasons.talk.realtime.FakeRealtimeSessionIssuer;
import com.jglearning.jg4seasons.talk.realtime.RealtimeSessionIssuer;
import com.jglearning.jg4seasons.talk.realtime.RealtimeSessionRequest;
import com.jglearning.jg4seasons.talk.realtime.RealtimeUnavailableException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/** 발급기 고르기. 통합 테스트는 @Primary 가짜가 가려서 운영 기본값(none) 빈을 부르지 않는다 — 여기서 직접(리뷰 7) */
class TalkRealtimeConfigTest {

    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-26T03:00:00Z"), ZoneOffset.UTC);
    static final RealtimeSessionRequest REQUEST = new RealtimeSessionRequest("m", "i", "marin", "t", 60);

    @Test
    void 기본값_none이면_발급은_503이_되는_예외() {
        RealtimeSessionIssuer issuer = new TalkRealtimeConfig()
                .realtimeSessionIssuer(
                        new TalkProperties(null, null), JsonMapper.builder().build(), CLOCK);

        assertThatThrownBy(() -> issuer.issue(REQUEST)).isInstanceOf(RealtimeUnavailableException.class);
    }

    @Test
    void fake면_가짜_발급기() {
        TalkProperties properties = new TalkProperties(
                new TalkProperties.Realtime(TalkProperties.Realtime.Type.FAKE, null, null, null, null, null, null),
                null);

        assertThat(new TalkRealtimeConfig()
                        .realtimeSessionIssuer(properties, JsonMapper.builder().build(), CLOCK))
                .isInstanceOf(FakeRealtimeSessionIssuer.class);
    }
}
