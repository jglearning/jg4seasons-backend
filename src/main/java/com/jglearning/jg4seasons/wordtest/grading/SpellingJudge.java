package com.jglearning.jg4seasons.wordtest.grading;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 철자 판정(B·C·D, 기획 §5-3). LLM은 글자를 옮겨 적기만 하고 맞았는지는 여기서 정한다.
 *
 * <p>순서: 읽을 수 없음 → wrong + unreadable(1단계, 2단계에서 학생 확인으로) / 빈칸 → blank / 정규화한 값이 정답·허용 답과 같으면 correct / 인쇄된 첫 글자가 한
 * 글자인 문항은 그 글자 뒤에 이어 쓴 나머지만 써도 correct / 그 밖은 wrong. 숙어 자리표시(~·one's·-ing)는 처리하지 않는다(2단계 허용 답).
 */
public final class SpellingJudge {

    /** 일반 공백·탭·줄바꿈에 NBSP(U+00A0)·전각 공백(U+3000) — 읽은 글자에 섞일 수 있다 */
    private static final Pattern SPACES = Pattern.compile("[\\s\\u00A0\\u3000]+");

    private SpellingJudge() {}

    public static Judgement judge(AnswerKey key, Reading reading) {
        if (reading.unreadable()) {
            return new Judgement(AnswerResult.WRONG, UnsureReason.UNREADABLE);
        }
        String written = normalize(reading.text());
        if (written.isEmpty()) {
            return new Judgement(AnswerResult.BLANK, null);
        }
        if (matches(key, written) || matches(key, continuedAfterHint(key, written))) {
            return new Judgement(AnswerResult.CORRECT, null);
        }
        return new Judgement(AnswerResult.WRONG, null);
    }

    /** 앞뒤 공백 제거, 소문자, 연속 공백 하나로. null은 빈 문자열 */
    public static String normalize(String text) {
        if (text == null) {
            return "";
        }
        return SPACES.matcher(text).replaceAll(" ").strip().toLowerCase(Locale.ROOT);
    }

    private static boolean matches(AnswerKey key, String written) {
        if (written == null) {
            return false;
        }
        if (normalize(key.answer()).equals(written)) {
            return true;
        }
        return key.accepted().stream().anyMatch(text -> normalize(text).equals(written));
    }

    /** 한 글자 hint 뒤에 이어 쓴 답이면 hint를 붙인 값, 아니면 null(숙어의 낱말별 hint에는 적용하지 않는다) */
    private static String continuedAfterHint(AnswerKey key, String written) {
        String hint = normalize(key.hint());
        if (hint.length() != 1) {
            return null;
        }
        return hint + written;
    }
}
