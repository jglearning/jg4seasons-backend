package com.jglearning.jg4seasons.listening;

import static org.assertj.core.api.Assertions.assertThat;

import com.jglearning.jg4seasons.IntegrationTest;
import com.jglearning.jg4seasons.TestData;
import com.jglearning.jg4seasons.listening.persistence.FlushStore;
import com.jglearning.jg4seasons.listening.persistence.FlushStore.SessionRow;
import com.jglearning.jg4seasons.listening.persistence.FlushStore.TrackProgressLock;
import com.jglearning.jg4seasons.listening.persistence.FlushStore.TrackProgressUpdate;
import com.jglearning.jg4seasons.listening.persistence.ProgressStore.LessonProgressRow;
import com.jglearning.jg4seasons.listening.segment.Range;
import com.jglearning.jg4seasons.listening.segment.SegmentFilter.Accepted;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** flush 저장소: 세션 upsert, 세그먼트 추가(원본), 집계 행 확보·잠금·갱신. 시각은 UTC로 저장한다. */
@IntegrationTest
@Transactional
class FlushStoreTest {

    static final String SESSION = "7c2e0000-0000-4000-8000-000000000001";
    static final Instant STARTED = Instant.parse("2026-11-10T02:59:00Z");
    static final Instant NOW = Instant.parse("2026-11-10T03:00:00Z");

    @Autowired
    FlushStore store;

    @Autowired
    JdbcTemplate jdbc;

    TestData data;
    long studentId;
    long otherStudentId;
    long lessonId;
    Map<String, Long> tracks;

    @BeforeEach
    void setUp() {
        data = new TestData(jdbc);
        long academyId = data.academy("테스트 학원");
        studentId = data.student(academyId, "김학생");
        otherStudentId = data.student(academyId, "이학생");
        tracks = data.lessonContent("L2-B1-05", 2, 2, List.of("1.0"));
        lessonId = data.lessonId("L2-B1-05");
    }

    @Test
    void 새_세션은_시각을_UTC로_저장한다() {
        long track = tracks.get("p1:1.0");

        SessionRow session = store.upsertSession(SESSION, studentId, track, STARTED, "UA", NOW);

        assertThat(session.studentId()).isEqualTo(studentId);
        assertThat(session.trackId()).isEqualTo(track);
        assertThat(jdbc.queryForObject(
                        "select started_at from listening_session where id = ?", LocalDateTime.class, session.id()))
                .isEqualTo(LocalDateTime.of(2026, 11, 10, 2, 59));
    }

    @Test
    void 같은_세션_다시는_last_seen만() {
        long track = tracks.get("p1:1.0");
        SessionRow first = store.upsertSession(SESSION, studentId, track, STARTED, "UA", NOW);

        SessionRow again = store.upsertSession(SESSION, studentId, track, STARTED, "다른 UA", NOW.plusSeconds(10));

        assertThat(again.id()).isEqualTo(first.id());
        assertThat(jdbc.queryForMap("select last_seen_at, user_agent from listening_session where id = ?", first.id()))
                .containsEntry("last_seen_at", LocalDateTime.of(2026, 11, 10, 3, 0, 10))
                .containsEntry("user_agent", "UA");
    }

    @Test
    void 다른_학생의_세션이면_그_학생_값을_돌려주고_고치지_않는다() {
        long track = tracks.get("p1:1.0");
        store.upsertSession(SESSION, otherStudentId, track, STARTED, "UA", NOW);

        SessionRow mine = store.upsertSession(SESSION, studentId, track, STARTED, "UA", NOW.plusSeconds(10));

        assertThat(mine.studentId()).isEqualTo(otherStudentId);
        assertThat(jdbc.queryForObject(
                        "select last_seen_at from listening_session where id = ?", LocalDateTime.class, mine.id()))
                .isEqualTo(LocalDateTime.of(2026, 11, 10, 3, 0));
    }

    @Test
    void 사용자_에이전트는_200자로_자른다() {
        SessionRow session =
                store.upsertSession(SESSION, studentId, tracks.get("p1:1.0"), STARTED, "a".repeat(250), NOW);

        assertThat(jdbc.queryForObject(
                        "select user_agent from listening_session where id = ?", String.class, session.id()))
                .hasSize(200);
    }

    @Test
    void 넣은_세그먼트를_받은_구간으로_돌려준다() {
        SessionRow session = store.upsertSession(SESSION, studentId, tracks.get("p1:1.0"), STARTED, null, NOW);

        store.insertSegments(
                session.id(), List.of(new Accepted(new Range(0, 5000), NOW), new Accepted(new Range(5000, 6000), NOW)));

        assertThat(store.receivedRanges(session.id()))
                .containsExactlyInAnyOrder(new Range(0, 5000), new Range(5000, 6000));
        assertThat(jdbc.queryForObject(
                        "select recorded_at from listening_segment where listening_session_id = ? and start_ms = 0",
                        LocalDateTime.class,
                        session.id()))
                .isEqualTo(LocalDateTime.of(2026, 11, 10, 3, 0));
    }

    @Test
    void 집계_행이_없으면_만들고_잠근다() {
        LessonProgressRow lesson = store.lockLessonProgress(studentId, lessonId, 2);
        TrackProgressLock track = store.lockTrackProgress(studentId, tracks.get("p1:1.0"), NOW);

        assertThat(lesson).isEqualTo(new LessonProgressRow(LessonProgressStatus.NOT_STARTED, 0, 2));
        assertThat(track.merged()).isEmpty();
        assertThat(track.coverage()).isEqualByComparingTo("0");
        assertThat(track.completedAt()).isNull();
        assertThat(track.totalPlayMs()).isZero();
        assertThat(track.sessionCount()).isZero();
    }

    @Test
    void 갱신한_값을_다시_읽는다() {
        long track = tracks.get("p1:1.0");
        store.lockLessonProgress(studentId, lessonId, 2);
        store.lockTrackProgress(studentId, track, NOW);

        store.updateTrackProgress(
                studentId,
                track,
                new TrackProgressUpdate(
                        List.of(new Range(0, 9000)), 9000, new BigDecimal("0.9000"), NOW, 9500, 1, NOW));
        store.updateLessonProgress(studentId, lessonId, LessonProgressStatus.IN_PROGRESS, 1, 2, null);

        TrackProgressLock reread = store.lockTrackProgress(studentId, track, NOW);
        assertThat(reread.merged()).containsExactly(new Range(0, 9000));
        assertThat(reread.coveredMs()).isEqualTo(9000);
        assertThat(reread.coverage()).isEqualByComparingTo("0.9");
        assertThat(reread.completedAt()).isEqualTo(NOW);
        assertThat(reread.totalPlayMs()).isEqualTo(9500);
        assertThat(reread.sessionCount()).isEqualTo(1);
        assertThat(store.lockLessonProgress(studentId, lessonId, 2))
                .isEqualTo(new LessonProgressRow(LessonProgressStatus.IN_PROGRESS, 1, 2));
        assertThat(jdbc.queryForMap(
                        "select required_done, required_total from lesson_progress where student_id = ? and lesson_id = ?",
                        studentId,
                        lessonId))
                .containsEntry("required_done", 1)
                .containsEntry("required_total", 2);
    }

    @Test
    void 완료된_트랙만_골라_읽는다() {
        long done = tracks.get("p1:1.0");
        long notDone = tracks.get("p2:1.0");
        store.lockTrackProgress(studentId, done, NOW);
        store.lockTrackProgress(studentId, notDone, NOW);
        store.updateTrackProgress(
                studentId,
                done,
                new TrackProgressUpdate(List.of(new Range(0, 9000)), 9000, BigDecimal.ONE, NOW, 9000, 1, NOW));

        assertThat(store.completedTracksForShare(studentId, List.of(done, notDone)))
                .containsExactly(done);
        assertThat(store.completedTracksForShare(otherStudentId, List.of(done, notDone)))
                .isEmpty();
        assertThat(store.completedTracksForShare(studentId, List.of())).isEmpty();
    }

    @Test
    void 완료된_트랙은_null로_갱신해도_완료_시각을_유지한다() {
        long track = tracks.get("p1:1.0");
        store.lockTrackProgress(studentId, track, NOW);
        store.updateTrackProgress(
                studentId,
                track,
                new TrackProgressUpdate(List.of(new Range(0, 9000)), 9000, BigDecimal.ONE, NOW, 9000, 1, NOW));

        store.updateTrackProgress(
                studentId,
                track,
                new TrackProgressUpdate(
                        List.of(new Range(0, 9000)), 9000, BigDecimal.ONE, null, 9000, 1, NOW.plusSeconds(60)));

        assertThat(store.lockTrackProgress(studentId, track, NOW).completedAt()).isEqualTo(NOW);
    }
}
