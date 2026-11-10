package com.jglearning.jg4seasons.words.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** 단어 학습 점수는 음수가 될 수 없다(어긋나면 데이터 오류). 단어 수와의 관계는 이 타입이 모른다. */
class WordStudyScoresTest {

    @Test
    void 영은_받는다() {
        assertThat(new WordStudyScores(0, 0, 0, 0).cardsViewed()).isZero();
    }

    @Test
    void 음수면_데이터_오류() {
        assertThatThrownBy(() -> new WordStudyScores(-1, 0, 0, 0)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new WordStudyScores(0, -1, 0, 0)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new WordStudyScores(0, 0, -1, 0)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new WordStudyScores(0, 0, 0, -1)).isInstanceOf(IllegalStateException.class);
    }
}
