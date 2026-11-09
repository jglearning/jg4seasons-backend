package com.jglearning.jg4seasons.listening;

import static org.assertj.core.api.Assertions.assertThat;

import com.jglearning.jg4seasons.IntegrationTest;
import com.jglearning.jg4seasons.TestData;
import com.jglearning.jg4seasons.listening.persistence.ProgressSummaryStore;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** 학원 현황용 청취 집계 요약(5.4): 회차 완료 학생 수, 구간 안에 들은 학생 수. */
@IntegrationTest
@Transactional
class ProgressSummaryStoreTest {

    @Autowired
    ProgressSummaryStore store;

    @Autowired
    JdbcTemplate jdbc;

    TestData data;
    long lessonId;
    long a;
    long b;
    long c;
    Map<String, Long> tracks;

    @BeforeEach
    void setUp() {
        data = new TestData(jdbc);
        long academyId = data.academy("요약 학원");
        a = data.student(academyId, "가");
        b = data.student(academyId, "나");
        c = data.student(academyId, "다");
        lessonId = data.lessonId("L2-B1-05");
        tracks = data.lessonContent("L2-B1-05", 2, 1, List.of("1.0"));
    }

    private void lessonProgress(long student, long lesson, String status) {
        jdbc.update(
                "insert into lesson_progress (student_id, lesson_id, status) values (?, ?, ?)",
                student,
                lesson,
                status);
    }

    /** lastPlayedAtUtc: DB에는 UTC로 저장 */
    private void played(long student, long trackId, String lastPlayedAtUtc) {
        jdbc.update(
                "insert into track_progress (student_id, track_id, merged_ranges, last_played_at)"
                        + " values (?, ?, '[]', ?)",
                student,
                trackId,
                LocalDateTime.parse(lastPlayedAtUtc));
    }

    @Test
    void 완료_학생만_센다() {
        lessonProgress(a, lessonId, "completed");
        lessonProgress(b, lessonId, "in_progress");
        lessonProgress(c, lessonId, "not_started");
        lessonProgress(b, data.lessonId("L2-B1-04"), "completed"); // 다른 회차 완료는 세지 않음

        assertThat(store.completedCount(lessonId, List.of(a, b, c))).isEqualTo(1);
    }

    @Test
    void 목록_밖_학생은_세지_않는다() {
        lessonProgress(a, lessonId, "completed");
        lessonProgress(b, lessonId, "completed");
        played(a, tracks.get("p1:1.0"), "2026-11-10T01:00:00");
        played(b, tracks.get("p1:1.0"), "2026-11-10T01:00:00");

        assertThat(store.completedCount(lessonId, List.of(a))).isEqualTo(1);
        assertThat(store.playedBetween(
                        List.of(a), Instant.parse("2026-11-10T00:00:00Z"), Instant.parse("2026-11-11T00:00:00Z")))
                .isEqualTo(1);
    }

    @Test
    void 빈_목록이면_0이고_쿼리를_보내지_않는다() {
        ProgressSummaryStore noDb = new ProgressSummaryStore(null); // 쿼리를 보내면 NullPointerException

        assertThat(noDb.completedCount(lessonId, List.of())).isZero();
        assertThat(noDb.playedBetween(List.of(), Instant.EPOCH, Instant.parse("2026-11-11T00:00:00Z")))
                .isZero();
    }

    @Test
    void 구간_끝은_포함하지_않고_시작은_포함한다() {
        played(a, tracks.get("p1:1.0"), "2026-11-10T00:00:00"); // 시작 = 포함
        played(b, tracks.get("p1:1.0"), "2026-11-11T00:00:00"); // 끝 = 제외
        played(c, tracks.get("p1:1.0"), "2026-11-09T23:59:59"); // 시작 전

        assertThat(store.playedBetween(
                        List.of(a, b, c), Instant.parse("2026-11-10T00:00:00Z"), Instant.parse("2026-11-11T00:00:00Z")))
                .isEqualTo(1);
    }

    @Test
    void 트랙_여러_개를_들어도_학생은_한_번() {
        played(a, tracks.get("p1:1.0"), "2026-11-10T01:00:00");
        played(a, tracks.get("p2:1.0"), "2026-11-10T02:00:00");

        assertThat(store.playedBetween(
                        List.of(a), Instant.parse("2026-11-10T00:00:00Z"), Instant.parse("2026-11-11T00:00:00Z")))
                .isEqualTo(1);
    }
}
