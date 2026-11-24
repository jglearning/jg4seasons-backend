package com.jglearning.jg4seasons.wordtest.grading;

import com.jglearning.jg4seasons.content.api.WordTestItem;
import java.util.ArrayList;
import java.util.List;

/**
 * 채점 조립: 문항(정답·허용 답·첫 글자)과 읽은 글자를 번호 순으로 짝지어 {@link SpellingJudge}로 판정한다. 점수 = 맞은 수(읽을 수 없음·빈칸은 0점). 저장 글자는 빈칸·읽을 수
 * 없음이면 NULL
 */
public final class Grading {

    private Grading() {}

    /** items와 readings는 같은 길이·같은 번호 순(ReadValidation이 보장). 아니면 IllegalArgumentException */
    public static Graded grade(List<WordTestItem> items, List<Reading> readings) {
        if (items.size() != readings.size()) {
            throw new IllegalArgumentException("문항 " + items.size() + "개와 읽은 " + readings.size() + "개가 다르다");
        }
        List<GradedAnswer> answers = new ArrayList<>(items.size());
        int score = 0;
        for (int i = 0; i < items.size(); i++) {
            WordTestItem item = items.get(i);
            Reading reading = readings.get(i);
            Judgement judgement =
                    SpellingJudge.judge(new AnswerKey(item.answer(), item.accepted(), item.hint()), reading);
            String stored = judgement.result() == AnswerResult.BLANK || reading.unreadable() ? null : reading.text();
            answers.add(new GradedAnswer(item.id(), stored, judgement.result(), judgement.unsureReason()));
            if (judgement.result() == AnswerResult.CORRECT) {
                score++;
            }
        }
        return new Graded(List.copyOf(answers), score);
    }
}
