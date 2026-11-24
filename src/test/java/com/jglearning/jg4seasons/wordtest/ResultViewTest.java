package com.jglearning.jg4seasons.wordtest;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** 응답의 쓴 글자. 결과 값은 DB 판정 그대로(unclear 포함 — 11/25부터 DB에 저장) */
class ResultViewTest {

    @Test
    void 쓴_글자가_없으면_빈_문자열() {
        assertThat(ResultView.written(null)).isEmpty();
        assertThat(ResultView.written("beautful")).isEqualTo("beautful");
    }
}
