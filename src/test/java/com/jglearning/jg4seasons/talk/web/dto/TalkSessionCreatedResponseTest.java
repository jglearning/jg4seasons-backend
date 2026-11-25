package com.jglearning.jg4seasons.talk.web.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

/** 응답 객체가 로그·예외 메시지에 들어가도 임시 키가 새지 않게(리뷰 8) */
class TalkSessionCreatedResponseTest {

    @Test
    void toString에_임시_키가_없다() {
        TalkSessionCreatedResponse.Realtime realtime = new TalkSessionCreatedResponse.Realtime(
                "ek_secret_value", Instant.parse("2026-11-26T03:01:00Z"), "m", "wrap up");
        TalkSessionCreatedResponse response = new TalkSessionCreatedResponse(1, "created", 180, realtime);

        assertThat(realtime.toString()).doesNotContain("ek_secret_value").contains("2026-11-26T03:01:00Z");
        assertThat(response.toString()).doesNotContain("ek_secret_value");
    }
}
