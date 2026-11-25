package com.jglearning.jg4seasons.talk;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

/** 세션 상태 값(계약 D-0, V15 CHECK와 같은 넷) */
class TalkSessionStatusTest {

    @Test
    void code는_계약의_네_값() {
        assertThat(Arrays.stream(TalkSessionStatus.values()).map(TalkSessionStatus::code))
                .containsExactly("created", "ended", "expired", "failed");
    }

    @Test
    void ofCode는_대소문자까지_같은_값만() {
        assertThat(TalkSessionStatus.ofCode("ended")).contains(TalkSessionStatus.ENDED);
        assertThat(TalkSessionStatus.ofCode("Ended")).isEmpty();
        assertThat(TalkSessionStatus.ofCode("done")).isEmpty();
    }
}
