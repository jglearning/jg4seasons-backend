package com.jglearning.jg4seasons.talk.summary;

import com.jglearning.jg4seasons.talk.TalkRole;
import com.jglearning.jg4seasons.talk.TalkTranscript.Turn;
import com.jglearning.jg4seasons.talk.summary.TalkSummary.Correction;
import java.util.List;
import java.util.Objects;

/**
 * 모델 요약을 화면에 내기 전에 코드가 다시 거른다(작업 지시 A-2 원칙, 순수 함수). 프롬프트가 같은 규칙을 말하지만 모델이 어겨도 지어낸 문장이 학생에게 가지 않게:
 *
 * <ul>
 *   <li>said가 학생 줄 어디에도 그대로 들어 있지 않으면 그 고침은 버린다(캐릭터 줄·지어낸 문장 차단). 한 줄에 여러 문장이 있으면 그중 한 문장만 골라도 된다
 *   <li>학생 줄이 2개 미만이면 고침은 비운다
 *   <li>잘 쓴 표현 최대 2개, 고침 최대 3개, 문자열은 앞뒤 공백 제거 후 200자까지, 빈 값은 버린다
 * </ul>
 */
public final class SummaryGuard {

    static final int MAX_GOOD = 2;
    static final int MAX_CORRECTIONS = 3;
    static final int MAX_TEXT = 200;

    private SummaryGuard() {}

    public static TalkSummary apply(TalkSummary raw, List<Turn> turns) {
        List<String> studentLines = turns.stream()
                .filter(turn -> turn.role() == TalkRole.STUDENT)
                .map(Turn::text)
                .toList();
        List<String> good = raw.goodExpressions().stream()
                .map(SummaryGuard::clean)
                .filter(Objects::nonNull)
                .limit(MAX_GOOD)
                .toList();
        List<Correction> corrections = studentLines.size() < 2
                ? List.of()
                : raw.corrections().stream()
                        .map(SummaryGuard::clean)
                        .filter(Objects::nonNull)
                        .filter(correction -> studentLines.stream().anyMatch(line -> line.contains(correction.said())))
                        .limit(MAX_CORRECTIONS)
                        .toList();
        return new TalkSummary(good, corrections);
    }

    private static Correction clean(Correction correction) {
        String said = correction.said() == null ? null : correction.said().strip();
        String better = clean(correction.better());
        String note = clean(correction.note());
        if (said == null || said.isEmpty() || better == null || note == null) {
            return null;
        }
        // said는 학생 줄과 비교하므로 자르지 않는다(학생 줄은 이미 500자 이내)
        return new Correction(said, better, note);
    }

    /** 앞뒤 공백 제거 후 200자(코드 포인트), 비면 null */
    private static String clean(String text) {
        if (text == null) {
            return null;
        }
        String stripped = text.strip();
        if (stripped.isEmpty()) {
            return null;
        }
        if (stripped.codePointCount(0, stripped.length()) <= MAX_TEXT) {
            return stripped;
        }
        return stripped.substring(0, stripped.offsetByCodePoints(0, MAX_TEXT));
    }
}
