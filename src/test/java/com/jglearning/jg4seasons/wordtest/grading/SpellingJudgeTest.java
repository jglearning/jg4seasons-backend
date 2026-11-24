package com.jglearning.jg4seasons.wordtest.grading;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * 철자 판정(기획 §5-3, 11/24 결정): 정규화 → 빈칸 → 정답·허용 답 → 한 글자 hint 뒤에 이어 쓴 답. 읽을 수 없음은 wrong + unreadable. 숙어 자리표시(~·one's·-ing)는
 * 처리하지 않는다(2단계).
 */
class SpellingJudgeTest {

    @ParameterizedTest(name = "[{index}] {0} / 허용 {1} / hint {2} / 쓴 것 \"{3}\" 읽기불가 {4} → {5} {6}")
    @CsvSource(
            delimiter = ';',
            nullValues = "-",
            value = {
                // 정답·대소문자·앞뒤 공백·한 글자 틀림
                "cheerful;-;-;cheerful;false;CORRECT;-",
                "cheerful;-;-;Cheerful;false;CORRECT;-",
                "cheerful;-;-;'  cheerful  ';false;CORRECT;-",
                "cheerful;-;-;cheerfull;false;WRONG;-",
                // 빈칸
                "cheerful;-;-;'';false;BLANK;-",
                "cheerful;-;-;'   ';false;BLANK;-",
                "cheerful;-;-;-;false;BLANK;-",
                // 읽을 수 없음이 정답 글자·빈칸보다 먼저
                "cheerful;-;-;cheerful;true;WRONG;UNREADABLE",
                "cheerful;-;-;-;true;WRONG;UNREADABLE",
                // 숙어: 띄어쓰기 정규화, 낱말별 hint에는 빼기 규칙 없음
                "point to;-;p... t...;'point  to';false;CORRECT;-",
                "point to;-;p... t...;pointto;false;WRONG;-",
                "point to;-;p... t...;to;false;WRONG;-",
                "adapt A to B;-;a... A t... B;adapt a to b;false;CORRECT;-",
                // 한 글자 hint: 다시 써도, 이어 써도 맞음
                "atmosphere;-;a;atmosphere;false;CORRECT;-",
                "atmosphere;-;a;tmosphere;false;CORRECT;-",
                "atmosphere;-;a;Tmosphere;false;CORRECT;-",
                "atmosphere;-;a;a;false;WRONG;-",
                "atmosphere;-;a;mosphere;false;WRONG;-",
                // 이어 쓰기 규칙은 한 글자 hint에만
                "about;-;ab;out;false;WRONG;-",
                // 허용 답
                "judgment;judgement;-;judgement;false;CORRECT;-",
                "judgment;judgement;-;Judgment;false;CORRECT;-",
                "judgment;judgement|judgemint;-;judgemint;false;CORRECT;-",
                // 다른 공백 문자(NBSP, 전각 공백, 탭)
                "as a result;-;a... a r...;as a　result;false;CORRECT;-",
                "as a result;-;a... a r...;as\ta result;false;CORRECT;-",
                // 숙어 자리표시는 2단계
                "be used to -ing;-;-;be used to;false;WRONG;-",
            })
    void 판정표(
            String answer,
            String accepted,
            String hint,
            String written,
            boolean unreadable,
            AnswerResult result,
            UnsureReason reason) {
        AnswerKey key =
                new AnswerKey(answer, accepted == null ? List.of() : Arrays.asList(accepted.split("\\|")), hint);

        Judgement judgement = SpellingJudge.judge(key, new Reading(written, unreadable));

        assertThat(judgement).isEqualTo(new Judgement(result, reason));
    }

    @Test
    void 정규화는_앞뒤_공백을_지우고_소문자로_연속_공백을_하나로() {
        assertThat(SpellingJudge.normalize("  Be  Connected\t\tTo ")).isEqualTo("be connected to");
        assertThat(SpellingJudge.normalize(null)).isEmpty();
    }

    @Test
    void 정답이_비면_거부한다() {
        assertThatThrownBy(() -> new AnswerKey(" ", List.of(), null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AnswerKey(null, List.of(), null)).isInstanceOf(IllegalArgumentException.class);
    }
}
