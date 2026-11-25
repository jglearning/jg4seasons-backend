package com.jglearning.jg4seasons.talk;

import static org.assertj.core.api.Assertions.assertThat;

import com.jglearning.jg4seasons.talk.TalkTranscript.RawTurn;
import com.jglearning.jg4seasons.talk.TalkTranscript.Trimmed;
import com.jglearning.jg4seasons.talk.TalkTranscript.Turn;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 종료 요청의 글자 기록 자르기(작업 지시 A-2): turns 200개, 한 줄 500자, durationSec는 max_seconds까지 */
class TalkTranscriptTest {

    @Test
    void turns는_앞에서부터_200개까지() {
        List<RawTurn> raw = new ArrayList<>();
        for (int i = 1; i <= 201; i++) {
            raw.add(new RawTurn(i % 2 == 0 ? TalkRole.STUDENT : TalkRole.CHARACTER, "line " + i));
        }

        Trimmed trimmed = TalkTranscript.trim(raw, 60, 180);

        assertThat(trimmed.turns()).hasSize(200);
        assertThat(trimmed.turns().get(0).text()).isEqualTo("line 1");
        assertThat(trimmed.turns().get(199).text()).isEqualTo("line 200");
    }

    @Test
    void 한_줄은_500자까지_글자를_반으로_자르지_않는다() {
        String emojiAtCut = "a".repeat(499) + "😀" + "b";

        Turn turn = TalkTranscript.trim(
                        List.of(
                                new RawTurn(TalkRole.STUDENT, "x".repeat(600)),
                                new RawTurn(TalkRole.STUDENT, emojiAtCut)),
                        60,
                        180)
                .turns()
                .get(1);

        assertThat(TalkTranscript.trim(List.of(new RawTurn(TalkRole.STUDENT, "x".repeat(600))), 60, 180)
                        .turns()
                        .get(0)
                        .text())
                .hasSize(500);
        assertThat(turn.text().codePointCount(0, turn.text().length())).isEqualTo(500);
        assertThat(turn.text()).endsWith("😀");
    }

    @Test
    void 공백만_있는_줄은_버리고_seq는_1부터_다시() {
        Trimmed trimmed = TalkTranscript.trim(
                List.of(
                        new RawTurn(TalkRole.CHARACTER, "Hi!"),
                        new RawTurn(TalkRole.STUDENT, "   "),
                        new RawTurn(TalkRole.STUDENT, "  Hello.  ")),
                60,
                180);

        assertThat(trimmed.turns())
                .containsExactly(new Turn(1, TalkRole.CHARACTER, "Hi!"), new Turn(2, TalkRole.STUDENT, "Hello."));
    }

    @Test
    void durationSec는_max_seconds로_자른다() {
        assertThat(TalkTranscript.trim(List.of(), 400, 180).durationSec()).isEqualTo(180);
        assertThat(TalkTranscript.trim(List.of(), 168, 180).durationSec()).isEqualTo(168);
        assertThat(TalkTranscript.trim(List.of(), 0, 180).durationSec()).isZero();
    }

    @Test
    void 학생_차례_수는_자른_뒤_student_줄_수() {
        Trimmed trimmed = TalkTranscript.trim(
                List.of(
                        new RawTurn(TalkRole.CHARACTER, "Hi!"),
                        new RawTurn(TalkRole.STUDENT, "Hello."),
                        new RawTurn(TalkRole.STUDENT, " "),
                        new RawTurn(TalkRole.CHARACTER, "How are you?"),
                        new RawTurn(TalkRole.STUDENT, "Good.")),
                60,
                180);

        assertThat(trimmed.studentTurnCount()).isEqualTo(2);
    }

    @Test
    void 역할_code는_student와_character() {
        assertThat(TalkRole.ofCode("student")).contains(TalkRole.STUDENT);
        assertThat(TalkRole.ofCode("character")).contains(TalkRole.CHARACTER);
        assertThat(TalkRole.ofCode("teacher")).isEmpty();
        assertThat(TalkEndReason.ofCode("timeout")).contains(TalkEndReason.TIMEOUT);
        assertThat(TalkEndReason.ofCode("quit")).isEmpty();
    }

    @Test
    void 학생_줄_수는_줄_목록에서_계산하고_음수_시간은_거부() {
        // 리뷰 7: 따로 받는 칸이면 줄 목록과 어긋난 값이 저장될 수 있다
        Trimmed trimmed =
                new Trimmed(List.of(new Turn(1, TalkRole.STUDENT, "a"), new Turn(2, TalkRole.CHARACTER, "b")), 10);

        assertThat(trimmed.studentTurnCount()).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new Trimmed(List.of(), -1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
