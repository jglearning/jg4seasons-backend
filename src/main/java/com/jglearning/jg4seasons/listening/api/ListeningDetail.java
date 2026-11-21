package com.jglearning.jg4seasons.listening.api;

import com.jglearning.jg4seasons.content.api.LessonContent;
import java.util.Map;

/**
 * 학생 묶음 × 회차들의 청취 집계 한 번 읽기. 지문 요약은 이 트랙 집계로 계산한 것이라 둘이 서로 어긋나지 않는다.
 *
 * <p>두 맵은 약속이 다르다. 맵을 직접 읽지 말고 {@link #tracksOf}·{@link #passageOf}로 읽는다 — 트랙은 없으면 "안 들음"이지만, 지문 요약은 없으면 조립 오류다
 * (없는 것을 0·미완료로 읽으면 묶음에 없는 학생이나 넘기지 않은 회차의 지문이 "안 들음"으로 나간다).
 *
 * @param tracks 학생 id → 트랙 id → 집계. 집계 행이 있는 것만(없으면 안 들음)
 * @param passages 학생 id → 지문 id(LessonContent.Passage.id) → 요약. 묶음의 모든 학생 × 넘긴 회차의 모든 지문이 들어 있다(안 들은 지문은 0·미완료). 넘긴
 *     회차가 없으면 빈 맵. 안쪽 맵은 만드는 쪽이 바꿀 수 없게 넘긴다
 */
public record ListeningDetail(
        Map<Long, Map<Long, TrackProgressSummary>> tracks, Map<Long, Map<Long, PassageProgressSummary>> passages) {

    public ListeningDetail {
        tracks = Map.copyOf(tracks);
        passages = Map.copyOf(passages);
    }

    /** 그 학생의 트랙 id → 집계. 들은 트랙만 — 한 번도 듣지 않은 학생은 빈 맵 */
    public Map<Long, TrackProgressSummary> tracksOf(long studentId) {
        return tracks.getOrDefault(studentId, Map.of());
    }

    /**
     * 그 학생의 그 지문 요약. 조회에 넘긴 묶음의 학생·넘긴 회차의 지문이면 반드시 있다. 없으면 조립 오류(IllegalStateException → 500) — 0·미완료로 대신하지 않는다.
     * 지문은 번호(no)가 아니라 id로 찾는다(회차가 여럿이면 번호가 겹친다).
     */
    public PassageProgressSummary passageOf(long studentId, LessonContent.Passage passage) {
        Map<Long, PassageProgressSummary> mine = passages.get(studentId);
        if (mine == null) {
            throw new IllegalStateException("지문 요약에 학생이 없다: student " + studentId);
        }
        PassageProgressSummary summary = mine.get(passage.id());
        if (summary == null) {
            throw new IllegalStateException("지문 요약에 지문이 없다: student " + studentId + " passage " + passage.id());
        }
        return summary;
    }
}
