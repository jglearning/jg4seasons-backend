package com.jglearning.jg4seasons.talk;

import java.util.ArrayList;
import java.util.List;

/**
 * 종료 요청의 글자 기록 자르기(작업 지시 A-2, 순수 함수). 공백만 있는 줄은 버리고(받아쓰기가 비어 오는 경우), 남은 줄을 앞에서부터 200개까지, 한 줄은 앞뒤 공백을 지운 뒤
 * 500자(코드 포인트 — 이모지를 반으로 자르지 않게)까지. durationSec는 그 세션의 max_seconds까지. 저장량과 요약 입력의 상한이 여기서 정해진다
 */
public final class TalkTranscript {

    public static final int MAX_TURNS = 200;
    public static final int MAX_TEXT = 500;

    /** 브라우저가 보낸 줄 */
    public record RawTurn(TalkRole role, String text) {}

    /** 저장할 줄. seq는 1부터 */
    public record Turn(int seq, TalkRole role, String text) {}

    /**
     * 자른 결과
     *
     * @param studentTurnCount 자른 뒤 학생 줄 수(화면 "내가 말한 문장 수")
     */
    public record Trimmed(List<Turn> turns, int durationSec, int studentTurnCount) {

        public Trimmed {
            turns = List.copyOf(turns);
        }
    }

    private TalkTranscript() {}

    /** durationSec는 0 이상(음수는 요청 검증에서 400) */
    public static Trimmed trim(List<RawTurn> raw, int durationSec, int maxSeconds) {
        List<Turn> turns = new ArrayList<>();
        int students = 0;
        for (RawTurn line : raw) {
            if (turns.size() == MAX_TURNS) {
                break;
            }
            String text = line.text().strip();
            if (text.isEmpty()) {
                continue;
            }
            turns.add(new Turn(turns.size() + 1, line.role(), cut(text)));
            if (line.role() == TalkRole.STUDENT) {
                students++;
            }
        }
        return new Trimmed(turns, Math.min(durationSec, maxSeconds), students);
    }

    private static String cut(String text) {
        if (text.codePointCount(0, text.length()) <= MAX_TEXT) {
            return text;
        }
        return text.substring(0, text.offsetByCodePoints(0, MAX_TEXT));
    }
}
