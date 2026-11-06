package com.jglearning.jg4seasons.content.persistence;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** 회차 내용 읽기(콘텐츠 테이블, 테넌트 무관). 회차 단위로 테이블마다 한 번씩 읽는다(N+1 없음). */
@Component
public class LessonContentStore {

    public record LessonRow(
            long id,
            long bookId,
            String code,
            int number,
            String title,
            String type,
            String levelCode,
            String levelNameKo,
            String levelColorHex,
            BigDecimal coverageThreshold,
            String requiredTracksJson) {}

    public record PassageRow(long id, int no, String title, int wordCount) {}

    public record SentenceRow(long id, long passageId, int no, String text, String speaker) {}

    public record TrackRow(long id, Long passageId, String variant, String fileKey, int durationMs) {}

    public record TimingRow(long trackId, long sentenceId, int startMs, int endMs, boolean approximate) {}

    private final JdbcTemplate jdbc;

    public LessonContentStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 회차 + 레벨 + 그 레벨 완료 규칙(규칙이 없으면 coverageThreshold·requiredTracksJson이 null — 서비스가 데이터 오류로 다룬다) */
    public Optional<LessonRow> findLesson(String code) {
        return jdbc
                .query(
                        "select l.id, l.book_id, l.code, l.number, l.title, l.type,"
                                + " v.code as level_code, v.name_ko, v.color_hex,"
                                + " r.coverage_threshold, r.required_tracks"
                                + " from lesson l"
                                + " join book b on b.id = l.book_id"
                                + " join level v on v.id = b.level_id"
                                + " left join completion_rule r on r.level_id = v.id"
                                + " where l.code = ?",
                        (rs, i) -> new LessonRow(
                                rs.getLong("id"),
                                rs.getLong("book_id"),
                                rs.getString("code"),
                                rs.getInt("number"),
                                rs.getString("title"),
                                rs.getString("type"),
                                rs.getString("level_code"),
                                rs.getString("name_ko"),
                                rs.getString("color_hex"),
                                rs.getBigDecimal("coverage_threshold"),
                                rs.getString("required_tracks")),
                        code)
                .stream()
                .findFirst();
    }

    /** no 순 */
    public List<PassageRow> passages(long lessonId) {
        return jdbc.query(
                "select id, no, title, word_count from passage where lesson_id = ? order by no",
                (rs, i) -> new PassageRow(
                        rs.getLong("id"), rs.getInt("no"), rs.getString("title"), rs.getInt("word_count")),
                lessonId);
    }

    /** 지문 no·문장 no 순 */
    public List<SentenceRow> sentences(long lessonId) {
        return jdbc.query(
                "select s.id, s.passage_id, s.no, s.text, s.speaker from sentence s"
                        + " join passage p on p.id = s.passage_id"
                        + " where p.lesson_id = ? order by p.no, s.no",
                (rs, i) -> new SentenceRow(
                        rs.getLong("id"),
                        rs.getLong("passage_id"),
                        rs.getInt("no"),
                        rs.getString("text"),
                        rs.getString("speaker")),
                lessonId);
    }

    /** 지문 트랙(kind=passage)만, sort_order 순. passage_id가 없거나 다른 회차 지문이어도 읽는다(서비스가 데이터 오류로 다룬다) */
    public List<TrackRow> passageTracks(long lessonId) {
        return jdbc.query(
                "select id, passage_id, variant, file_key, duration_ms from track"
                        + " where lesson_id = ? and kind = 'passage'"
                        + " order by sort_order, id",
                (rs, i) -> new TrackRow(
                        rs.getLong("id"),
                        rs.getObject("passage_id", Long.class),
                        rs.getString("variant"),
                        rs.getString("file_key"),
                        rs.getInt("duration_ms")),
                lessonId);
    }

    /** 이 회차 지문 트랙들의 문장 시각 */
    public List<TimingRow> timings(long lessonId) {
        return jdbc.query(
                "select st.track_id, st.sentence_id, st.start_ms, st.end_ms, st.approximate from sentence_timing st"
                        + " join track t on t.id = st.track_id"
                        + " where t.lesson_id = ? and t.kind = 'passage'",
                (rs, i) -> new TimingRow(
                        rs.getLong("track_id"),
                        rs.getLong("sentence_id"),
                        rs.getInt("start_ms"),
                        rs.getInt("end_ms"),
                        rs.getBoolean("approximate")),
                lessonId);
    }

    public int wordCount(long lessonId) {
        Integer count = jdbc.queryForObject("select count(*) from word where lesson_id = ?", Integer.class, lessonId);
        return count == null ? 0 : count;
    }
}
