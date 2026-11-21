package com.jglearning.jg4seasons.listening;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jglearning.jg4seasons.IntegrationTest;
import com.jglearning.jg4seasons.TestData;
import com.jglearning.jg4seasons.listening.api.LessonProgressStatus;
import com.jglearning.jg4seasons.listening.persistence.ProgressStore;
import com.jglearning.jg4seasons.listening.persistence.ProgressStore.LessonProgressRow;
import com.jglearning.jg4seasons.listening.persistence.ProgressStore.TrackProgressRow;
import com.jglearning.jg4seasons.listening.segment.Range;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** 화면이 읽는 청취 집계(ADR-06): track_progress·lesson_progress. */
@IntegrationTest
@Transactional
class ProgressStoreTest {

    @Autowired
    ProgressStore store;

    @Autowired
    JdbcTemplate jdbc;

    TestData data;
    long studentId;
    long otherStudentId;
    Map<String, Long> tracks;

    @BeforeEach
    void setUp() {
        data = new TestData(jdbc);
        long academyId = data.academy("테스트 학원");
        studentId = data.student(academyId, "김학생");
        otherStudentId = data.student(academyId, "이학생");
        tracks = data.lessonContent("L2-B1-05", 1, 2, List.of("us_m", "us_w"));
    }

    private void trackProgress(long student, long trackId, String ranges, String coverage, boolean completed) {
        jdbc.update(
                "insert into track_progress (student_id, track_id, merged_ranges, covered_ms, coverage, completed_at,"
                        + " last_played_at) values (?, ?, ?, 0, ?, ?, '2026-11-05 10:00:00')",
                student,
                trackId,
                ranges,
                new BigDecimal(coverage),
                completed ? "2026-11-05 10:00:00" : null);
    }

    @Test
    void 트랙_집계를_읽는다() {
        long one = tracks.get("p1:us_w");
        long other = tracks.get("p1:us_m");
        trackProgress(studentId, one, "[[0,8120],[8400,61000]]", "0.9400", true);
        trackProgress(studentId, other, "[]", "0.0000", false);

        Map<Long, TrackProgressRow> found = store.trackProgress(studentId, List.of(one, other));

        assertThat(found.get(one).coverage()).isEqualByComparingTo("0.94");
        assertThat(found.get(one).completed()).isTrue();
        assertThat(found.get(one).mergedRanges()).containsExactly(new Range(0, 8120), new Range(8400, 61000));
        assertThat(found.get(other).completed()).isFalse();
        assertThat(found.get(other).mergedRanges()).isEmpty();
    }

    @Test
    void 다른_학생_집계는_안_읽힌다() {
        long one = tracks.get("p1:us_w");
        trackProgress(otherStudentId, one, "[[0,1000]]", "0.1000", false);

        assertThat(store.trackProgress(studentId, List.of(one))).isEmpty();
    }

    @Test
    void 트랙이_없으면_빈_결과() {
        assertThat(store.trackProgress(studentId, List.of())).isEmpty();
    }

    @Test
    void 회차_집계를_읽는다() {
        long lessonId = data.lessonId("L2-B1-05");
        jdbc.update(
                "insert into lesson_progress (student_id, lesson_id, status, passages_total, passages_done)"
                        + " values (?, ?, 'in_progress', 6, 4)",
                studentId,
                lessonId);

        jdbc.update(
                "insert into lesson_progress (student_id, lesson_id, status, passages_total, passages_done)"
                        + " values (?, ?, 'completed', 6, 6)",
                studentId,
                data.lessonId("L2-B1-04"));

        assertThat(store.lessonProgress(studentId, lessonId))
                .as("같은 학생의 다른 회차와 섞이지 않음")
                .contains(new LessonProgressRow(LessonProgressStatus.IN_PROGRESS, 4, 6));
        assertThat(store.lessonProgress(otherStudentId, lessonId)).isEmpty();
    }

    @Test
    void 구간이_뒤집혔거나_청취율이_1을_넘으면_IllegalState() {
        long one = tracks.get("p1:us_w");
        long other = tracks.get("p1:us_m");
        trackProgress(studentId, one, "[[5000,100]]", "0.5000", false);
        trackProgress(otherStudentId, other, "[[0,100]]", "1.2000", false);

        assertThatThrownBy(() -> store.trackProgress(studentId, List.of(one)))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> store.trackProgress(otherStudentId, List.of(other)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 여러_회차_진도를_한_번에() {
        long l4 = data.lessonId("L2-B1-04");
        long l5 = data.lessonId("L2-B1-05");
        long l6 = data.lessonId("L2-B1-06");
        for (Object[] row : new Object[][] {
            {studentId, l4, "completed", 6, 6},
            {studentId, l5, "in_progress", 6, 2},
            {otherStudentId, l6, "in_progress", 6, 1}
        }) {
            jdbc.update(
                    "insert into lesson_progress (student_id, lesson_id, status, passages_total, passages_done)"
                            + " values (?, ?, ?, ?, ?)",
                    row);
        }

        Map<Long, LessonProgressRow> found = store.lessonProgress(studentId, List.of(l4, l5, l6));

        assertThat(found)
                .containsOnlyKeys(l4, l5)
                .containsEntry(l4, new LessonProgressRow(LessonProgressStatus.COMPLETED, 6, 6))
                .containsEntry(l5, new LessonProgressRow(LessonProgressStatus.IN_PROGRESS, 2, 6));
    }

    @Test
    void 회차_목록이_비면_빈_결과() {
        assertThat(store.lessonProgress(studentId, List.of())).isEmpty();
    }

    @Test
    void 지문_집계는_그_학생의_행만_읽는다() {
        long track = tracks.get("p1:us_w");
        long passageId = jdbc.queryForObject("select passage_id from track where id = ?", Long.class, track);
        jdbc.update(
                "insert into passage_progress (student_id, passage_id, ref_track_id, ref_duration_ms, merged_ranges,"
                        + " covered_ms, coverage, completed_at) values (?, ?, ?, 6500, '[[0, 3250]]', 3250, 0.5000,"
                        + " '2026-11-05 10:00:00')",
                studentId,
                passageId,
                track);

        assertThat(store.passageProgress(studentId, List.of(passageId))).containsOnlyKeys(passageId);
        assertThat(store.passageProgress(studentId, List.of(passageId))
                        .get(passageId)
                        .completed())
                .isTrue();
        assertThat(store.passageProgress(otherStudentId, List.of(passageId)))
                .as("다른 학생")
                .isEmpty();
        assertThat(store.passageProgress(studentId, List.of())).isEmpty();
    }

    @Test
    void 깨진_지문_집계_행은_어느_학생의_어느_지문인지_오류에_남긴다() {
        // 여러 학생을 한 번에 읽는 화면(교사 4.4)에서는 행 하나가 깨져도 반 전체가 500이다 — 어느 행인지 찾을 수 있어야 한다
        long p1 = jdbc.queryForObject("select passage_id from track where id = ?", Long.class, tracks.get("p1:us_w"));
        jdbc.update(
                "insert into passage_progress (student_id, passage_id, ref_track_id, ref_duration_ms, merged_ranges,"
                        + " covered_ms, coverage) values (?, ?, ?, 6500, '[]', 0, 1.2000)",
                otherStudentId,
                p1,
                tracks.get("p1:us_w"));

        assertThatThrownBy(() -> store.passageProgressOf(List.of(studentId, otherStudentId), List.of(p1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("student " + otherStudentId)
                .hasMessageContaining("passage " + p1)
                .hasRootCauseMessage("지문 청취율은 0~1이어야 한다: 1.2000");
        assertThatThrownBy(() -> store.passageProgress(otherStudentId, List.of(p1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("student " + otherStudentId)
                .hasMessageContaining("passage " + p1);
    }

    @Test
    void 여러_학생의_지문_집계를_한_번에_읽는다() {
        long p1 = jdbc.queryForObject("select passage_id from track where id = ?", Long.class, tracks.get("p1:us_w"));
        long otherTrack = data.lessonContent("L2-B1-04", 1, 2, List.of("us_w")).get("p1:us_w");
        long p2 = jdbc.queryForObject("select passage_id from track where id = ?", Long.class, otherTrack);
        String insert = "insert into passage_progress (student_id, passage_id, ref_track_id, ref_duration_ms,"
                + " merged_ranges, covered_ms, coverage, completed_at) values (?, ?, ?, 6500, '[]', 0, 0.5000, ?)";
        jdbc.update(insert, studentId, p1, tracks.get("p1:us_w"), "2026-11-05 10:00:00");
        jdbc.update(insert, otherStudentId, p2, otherTrack, null);

        // 죽여야 하는 틀린 구현: 학생 조건 없는 쿼리(다른 학생의 행이 섞인다), 지문 조건 없는 쿼리
        assertThat(store.passageProgressOf(List.of(studentId), List.of(p1, p2))).containsOnlyKeys(studentId);
        assertThat(store.passageProgressOf(List.of(studentId), List.of(p1, p2)).get(studentId))
                .containsOnlyKeys(p1);
        assertThat(store.passageProgressOf(List.of(studentId, otherStudentId), List.of(p2)))
                .containsOnlyKeys(otherStudentId);

        Map<Long, Map<Long, com.jglearning.jg4seasons.listening.passage.StoredPassage>> both =
                store.passageProgressOf(List.of(studentId, otherStudentId), List.of(p1, p2));
        assertThat(both.get(studentId).get(p1).completed()).isTrue();
        assertThat(both.get(otherStudentId).get(p2).completed()).isFalse();
        assertThat(store.passageProgressOf(List.of(), List.of(p1))).isEmpty();
        assertThat(store.passageProgressOf(List.of(studentId), List.of())).isEmpty();
    }
}
