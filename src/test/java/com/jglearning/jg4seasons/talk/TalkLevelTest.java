package com.jglearning.jg4seasons.talk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class TalkLevelTest {

    @ParameterizedTest
    @CsvSource({"L1, mint", "L2, green", "L3, blue", "L4, navy", "K, black"})
    void 레벨_코드를_slug로_바꾼다(String code, String slug) {
        TalkLevel level = TalkLevel.ofLevelCode(code);

        assertThat(level.slug()).isEqualTo(slug);
        assertThat(level.levelCode()).isEqualTo(code);
    }

    @ParameterizedTest
    @CsvSource({"MINT, FEMALE", "GREEN, FEMALE", "BLUE, FEMALE", "NAVY, MALE", "BLACK, FEMALE"})
    void 레벨마다_캐릭터_목소리_성별이_정해져_있다(TalkLevel level, TalkVoice voice) {
        // 캐릭터 성별(사용자 11/25): 네이비만 남성
        assertThat(level.voice()).isEqualTo(voice);
    }

    @Test
    void 모르는_레벨_코드는_데이터_오류() {
        assertThatThrownBy(() -> TalkLevel.ofLevelCode("L5")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 주제_코드와_제목은_계약대로() {
        assertThat(TalkTopic.values())
                .extracting(TalkTopic::code, TalkTopic::title)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("lesson", "오늘 배운 단어로 이야기하기"),
                        org.assertj.core.groups.Tuple.tuple("day", "오늘 하루"),
                        org.assertj.core.groups.Tuple.tuple("likes", "내가 좋아하는 것"));
        assertThat(TalkTopic.ofCode("day")).contains(TalkTopic.DAY);
        assertThat(TalkTopic.ofCode("Day")).isEmpty();
    }
}
