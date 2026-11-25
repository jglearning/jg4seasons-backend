package com.jglearning.jg4seasons.talk.summary;

import static org.assertj.core.api.Assertions.assertThat;

import com.jglearning.jg4seasons.talk.TalkRole;
import com.jglearning.jg4seasons.talk.TalkTranscript.Turn;
import com.jglearning.jg4seasons.talk.summary.TalkSummary.Correction;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 모델 요약을 코드가 다시 거른다(작업 지시 A-2 원칙 — 프롬프트만 믿지 않음) */
class SummaryGuardTest {

    static final List<Turn> TURNS = List.of(
            new Turn(1, TalkRole.CHARACTER, "Hi! What did you do today?"),
            new Turn(2, TalkRole.STUDENT, "I go to school and play soccer."),
            new Turn(3, TalkRole.CHARACTER, "Nice! Who did you play with?"),
            new Turn(4, TalkRole.STUDENT, "My friend is play with me."));

    static final Correction REAL =
            new Correction("I go to school and play soccer.", "I went to school and played soccer.", "지난 일은 과거형으로 말해요");

    @Test
    void 학생이_말하지_않은_said는_버린다() {
        Correction madeUp = new Correction("I like apples.", "I like apples very much.", "지어낸 문장이에요");
        Correction characterLine = new Correction("Nice! Who did you play with?", "x", "캐릭터 말이에요");

        TalkSummary guarded = SummaryGuard.apply(
                new TalkSummary(List.of("play soccer"), List.of(madeUp, REAL, characterLine)), TURNS);

        assertThat(guarded.corrections()).containsExactly(REAL);
    }

    @Test
    void 학생_차례가_2번_미만이면_corrections는_빈_목록() {
        List<Turn> oneStudentTurn = TURNS.subList(0, 2);

        TalkSummary guarded =
                SummaryGuard.apply(new TalkSummary(List.of("play soccer"), List.of(REAL)), oneStudentTurn);

        assertThat(guarded.corrections()).isEmpty();
        assertThat(guarded.goodExpressions()).containsExactly("play soccer");
    }

    @Test
    void 개수와_길이를_자른다() {
        String longNote = "가".repeat(250);
        Correction second = new Correction("My friend is play with me.", "My friend played with me.", longNote);

        TalkSummary guarded = SummaryGuard.apply(
                new TalkSummary(
                        List.of("go to school", "play soccer", "my friend"), List.of(REAL, second, REAL, second)),
                TURNS);

        assertThat(guarded.goodExpressions()).containsExactly("go to school", "play soccer");
        assertThat(guarded.corrections()).hasSize(3);
        assertThat(guarded.corrections().get(1).note()).hasSize(200);
    }

    @Test
    void 빈_문자열과_빈_고침은_버린다() {
        Correction emptyBetter = new Correction("My friend is play with me.", " ", "고쳐 보세요");

        TalkSummary guarded =
                SummaryGuard.apply(new TalkSummary(List.of(" ", "play soccer"), List.of(emptyBetter)), TURNS);

        assertThat(guarded.goodExpressions()).containsExactly("play soccer");
        assertThat(guarded.corrections()).isEmpty();
    }

    @Test
    void 학생이_쓰지_않은_잘_쓴_표현은_버리고_대소문자는_가리지_않는다() {
        // 리뷰 6(사용자 A): 캐릭터 말·지어낸 표현이 "잘 쓴 표현"으로 나가지 않게. 모델이 첫 글자를 소문자로 옮기는 경우는 통과
        TalkSummary guarded = SummaryGuard.apply(
                new TalkSummary(List.of("Who did you play with", "I GO TO SCHOOL", "brilliant idea"), List.of()),
                TURNS);

        assertThat(guarded.goodExpressions()).containsExactly("I GO TO SCHOOL");
    }

    @Test
    void 한_줄의_여러_문장_중_한_문장만_골라도_통과하고_said는_대소문자까지_같아야_한다() {
        List<Turn> turns = List.of(
                new Turn(1, TalkRole.STUDENT, "I go to school. I play soccer."),
                new Turn(2, TalkRole.STUDENT, "It is fun."));
        Correction oneSentence = new Correction("I play soccer.", "I played soccer.", "과거형으로 말해요");
        Correction otherCase = new Correction("i play soccer.", "I played soccer.", "과거형으로 말해요");

        TalkSummary guarded = SummaryGuard.apply(new TalkSummary(List.of(), List.of(oneSentence, otherCase)), turns);

        assertThat(guarded.corrections()).containsExactly(oneSentence);
    }
}
