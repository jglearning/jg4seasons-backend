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

    public record Passage(int no, String title, int wordCount, List<Sentence> sentences, List<Speed> speeds) {
        public Passage {
            sentences = List.copyOf(sentences);
            speeds = List.copyOf(speeds);
        }
    }

    /** speaker는 대화문일 때만, 아니면 null */
    public record Sentence(int no, String text, String speaker) {}

    /**
     * 지문 트랙 하나(속도별). speed는 "1.0"처럼 variant(p1:1.0)의 속도 부분. timings[i]는 sentences[i]의 구간이고, 아직 만들지 않았으면 빈 목록.
     * timingsApproximate는 근사 시각이 하나라도 있으면 true
     */
    public record Speed(
            long trackId,
            String speed,
            String fileKey,
            int durationMs,
            List<TimeRange> timings,
            boolean timingsApproximate) {
        public Speed {
            timings = List.copyOf(timings);
        }
    }

    /** 지문 음원 안의 구간(ms) */
    public record TimeRange(int startMs, int endMs) {}
}
