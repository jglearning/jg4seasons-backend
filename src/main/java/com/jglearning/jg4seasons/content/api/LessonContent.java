package com.jglearning.jg4seasons.content.api;

import java.math.BigDecimal;
import java.util.List;

/** 회차 내용(테넌트 무관 콘텐츠). 회차 상세(2.3)의 재료. 목록은 모두 화면 순서(지문 no, 문장 no, 트랙 sort_order). */
public record LessonContent(
        long lessonId,
        long bookId,
        String code,
        int number,
        String title,
        String type,
        Level level,
        List<Passage> passages,
        int wordTotal,
        Rule rule) {

    public LessonContent {
        passages = List.copyOf(passages);
    }

    public record Level(String code, String nameKo, String colorHex) {}

    /** completion_rule 원문. 해석은 listening.completion이 한다(ADR-07) */
    public record Rule(BigDecimal coverageThreshold, String requiredTracksJson) {}

    public record Passage(int no, String title, int wordCount, List<Sentence> sentences, List<Voice> voices) {
        /** 목소리마다 시각은 비어 있거나(아직 없음) 문장 수와 같다 */
        public Passage {
            sentences = List.copyOf(sentences);
            voices = List.copyOf(voices);
            for (Voice voice : voices) {
                if (!voice.timings().isEmpty() && voice.timings().size() != sentences.size()) {
                    throw new IllegalArgumentException("문장 시각 수가 문장 수와 다르다: 지문 " + no);
                }
            }
        }
    }

    /** speaker는 대화문일 때만, 아니면 null */
    public record Sentence(int no, String text, String speaker) {}

    /**
     * 지문 트랙 하나(목소리별, ADR-27). voice는 "us_w"처럼 variant(p1:us_w)의 목소리 부분. timings[i]는 sentences[i]의 구간이고, 아직 만들지 않았으면
     * 빈 목록. timingsApproximate는 근사 시각이 하나라도 있으면 true(ADR-27부터 import는 근사를 만들지 않는다)
     */
    public record Voice(
            long trackId,
            String voice,
            String fileKey,
            int durationMs,
            List<TimeRange> timings,
            boolean timingsApproximate) {
        public Voice {
            timings = List.copyOf(timings);
        }
    }

    /** 지문 음원 안의 구간(ms). 0 <= 시작 < 끝(sentence_timing CHECK와 같음) */
    public record TimeRange(int startMs, int endMs) {
        public TimeRange {
            if (startMs < 0 || endMs <= startMs) {
                throw new IllegalArgumentException("구간은 0 <= 시작 < 끝이어야 한다: " + startMs + "~" + endMs);
            }
        }
    }
}
