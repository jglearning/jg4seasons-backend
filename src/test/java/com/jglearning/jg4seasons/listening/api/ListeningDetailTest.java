package com.jglearning.jg4seasons.listening.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jglearning.jg4seasons.content.api.LessonContent;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** 트랙 집계는 "행이 있는 것만", 지문 요약은 "모든 학생 × 모든 지문" — 두 약속이 다르다는 것을 읽는 메서드가 지킨다. */
class ListeningDetailTest {

    static final long STUDENT = 7;
    static final LessonContent.Passage FIRST = new LessonContent.Passage(101, 1, "Made-up", 10, List.of(), List.of());
    static final LessonContent.Passage SECOND = new LessonContent.Passage(102, 2, "Made-up", 10, List.of(), List.of());

    static final TrackProgressSummary TRACK =
            new TrackProgressSummary(new BigDecimal("0.5000"), false, 1000, 1, Instant.parse("2026-11-10T01:00:00Z"));
    static final PassageProgressSummary PASSAGE = new PassageProgressSummary(new BigDecimal("0.9500"), true);

    static ListeningDetail detail() {
        return new ListeningDetail(Map.of(STUDENT, Map.of(55L, TRACK)), Map.of(STUDENT, Map.of(FIRST.id(), PASSAGE)));
    }

    @Test
    void 트랙_집계가_없는_학생은_안_들은_것이다() {
        assertThat(detail().tracksOf(STUDENT)).containsOnlyKeys(55L);
        assertThat(detail().tracksOf(8)).as("행이 없으면 빈 맵").isEmpty();
    }

    @Test
    void 지문_요약은_있어야_하고_없으면_조립_오류다() {
        assertThat(detail().passageOf(STUDENT, FIRST)).isEqualTo(PASSAGE);
        // 죽여야 하는 틀린 구현: 없을 때 0·미완료를 돌려주는 것 — 묶음에 없는 학생, 넘기지 않은 회차의 지문이 "안 들음"으로 나간다
        assertThatThrownBy(() -> detail().passageOf(8, FIRST))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("학생")
                .hasMessageContaining("8");
        assertThatThrownBy(() -> detail().passageOf(STUDENT, SECOND))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("지문")
                .hasMessageContaining("102");
    }

    @Test
    void 지문은_번호가_아니라_id로_찾는다() {
        // 죽여야 하는 틀린 구현: 지문 번호(1~6)를 키로 쓰는 것 — 회차가 여럿이면 같은 번호가 겹친다
        LessonContent.Passage sameNoOtherLesson =
                new LessonContent.Passage(201, 1, "Made-up", 10, List.of(), List.of());

        assertThatThrownBy(() -> detail().passageOf(STUDENT, sameNoOtherLesson))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 만든_뒤에는_바꿀_수_없다() {
        Map<Long, Map<Long, TrackProgressSummary>> tracks = new HashMap<>();
        Map<Long, Map<Long, PassageProgressSummary>> passages = new HashMap<>();
        ListeningDetail detail = new ListeningDetail(tracks, passages);

        tracks.put(STUDENT, Map.of(55L, TRACK));
        passages.put(STUDENT, Map.of(FIRST.id(), PASSAGE));

        assertThat(detail.tracksOf(STUDENT)).as("넘긴 맵을 나중에 바꿔도 영향 없음").isEmpty();
        assertThatThrownBy(() -> detail.tracks().put(STUDENT, Map.of()))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> detail.passages().put(STUDENT, Map.of()))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
