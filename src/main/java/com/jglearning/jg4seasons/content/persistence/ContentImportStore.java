package com.jglearning.jg4seasons.content.persistence;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Component;

/** 콘텐츠 import의 회차 단위 읽기·쓰기(콘텐츠 테이블, 테넌트 무관). 트랜잭션은 부르는 쪽이 연다. */
@Component
public class ContentImportStore {

    public record LessonRow(long id, String title, int passageCount) {}

    public record PassageRow(long id, int no, String title, int wordCount) {}

    public record SentenceRow(long id, long passageId, int no, String text, String speaker) {}

    public record TrackRow(
            long id, Long passageId, String kind, String variant, String fileKey, int durationMs, int sortOrder) {}

    public record TimingRow(long sentenceId, long trackId, int startMs, int endMs, boolean approximate) {}

    public record WordRow(
            long id,
            Long passageId,
            int sortOrder,
            String english,
            String korean,
            String pos,
            String exampleEn,
            String exampleKo,
            Integer moeLevel,
            Long trackId,
            Long exampleTrackId) {}

    private final NamedParameterJdbcTemplate jdbc;

    public ContentImportStore(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 회차 행을 잠그고 읽는다(같은 회차를 동시에 넣지 않게) */
    public Optional<LessonRow> lockLesson(String code) {
        return jdbc
                .query(
                        "select id, title, passage_count from lesson where code = :code for update",
                        Map.of("code", code),
                        (rs, i) -> new LessonRow(rs.getLong("id"), rs.getString("title"), rs.getInt("passage_count")))
                .stream()
                .findFirst();
    }

    public void updateLesson(long id, String title, int passageCount) {
        jdbc.update(
                "update lesson set title = :title, passage_count = :count where id = :id",
                Map.of("id", id, "title", title, "count", passageCount));
    }

    public List<PassageRow> passages(long lessonId) {
        return jdbc.query(
                "select id, no, title, word_count from passage where lesson_id = :lesson",
                Map.of("lesson", lessonId),
                (rs, i) -> new PassageRow(
                        rs.getLong("id"), rs.getInt("no"), rs.getString("title"), rs.getInt("word_count")));
    }

    public long insertPassage(long lessonId, int no, String title, int wordCount) {
        return insert(
                "insert into passage (lesson_id, no, title, word_count) values (:lesson, :no, :title, :wordCount)",
                Map.of("lesson", lessonId, "no", no, "title", title, "wordCount", wordCount));
    }

    public void updatePassage(long id, String title, int wordCount) {
        jdbc.update(
                "update passage set title = :title, word_count = :wordCount where id = :id",
                Map.of("id", id, "title", title, "wordCount", wordCount));
    }

    public void deletePassages(Collection<Long> ids) {
        if (!ids.isEmpty()) {
            jdbc.update("delete from passage where id in (:ids)", Map.of("ids", ids));
        }
    }

    public List<SentenceRow> sentences(long lessonId) {
        return jdbc.query(
                "select s.id, s.passage_id, s.no, s.text, s.speaker from sentence s"
                        + " join passage p on p.id = s.passage_id where p.lesson_id = :lesson",
                Map.of("lesson", lessonId),
                (rs, i) -> new SentenceRow(
                        rs.getLong("id"),
                        rs.getLong("passage_id"),
                        rs.getInt("no"),
                        rs.getString("text"),
                        rs.getString("speaker")));
    }

    public long insertSentence(long passageId, int no, String text, String speaker) {
        return insert(
                "insert into sentence (passage_id, no, text, speaker) values (:passage, :no, :text, :speaker)",
                params("passage", passageId, "no", no, "text", text, "speaker", speaker));
    }

    public void updateSentence(long id, String text, String speaker) {
        jdbc.update(
                "update sentence set text = :text, speaker = :speaker where id = :id",
                params("id", id, "text", text, "speaker", speaker));
    }

    public void deleteSentences(Collection<Long> ids) {
        if (!ids.isEmpty()) {
            jdbc.update("delete from sentence where id in (:ids)", Map.of("ids", ids));
        }
    }

    public List<TrackRow> tracks(long lessonId) {
        return jdbc.query(
                "select id, passage_id, kind, variant, file_key, duration_ms, sort_order from track"
                        + " where lesson_id = :lesson",
                Map.of("lesson", lessonId),
                (rs, i) -> new TrackRow(
                        rs.getLong("id"),
                        rs.getObject("passage_id", Long.class),
                        rs.getString("kind"),
                        rs.getString("variant"),
                        rs.getString("file_key"),
                        rs.getInt("duration_ms"),
                        rs.getInt("sort_order")));
    }

    public long insertTrack(
            long lessonId, Long passageId, String kind, String variant, String fileKey, int durationMs, int sortOrder) {
        return insert(
                "insert into track (lesson_id, passage_id, kind, variant, file_key, duration_ms, sort_order)"
                        + " values (:lesson, :passage, :kind, :variant, :fileKey, :duration, :sortOrder)",
                params(
                        "lesson", lessonId,
                        "passage", passageId,
                        "kind", kind,
                        "variant", variant,
                        "fileKey", fileKey,
                        "duration", durationMs,
                        "sortOrder", sortOrder));
    }

    public void updateTrack(long id, Long passageId, String fileKey, int durationMs, int sortOrder) {
        jdbc.update(
                "update track set passage_id = :passage, file_key = :fileKey, duration_ms = :duration,"
                        + " sort_order = :sortOrder where id = :id",
                params(
                        "id",
                        id,
                        "passage",
                        passageId,
                        "fileKey",
                        fileKey,
                        "duration",
                        durationMs,
                        "sortOrder",
                        sortOrder));
    }

    public void deleteTracks(Collection<Long> ids) {
        if (!ids.isEmpty()) {
            jdbc.update("delete from track where id in (:ids)", Map.of("ids", ids));
        }
    }

    /** ids 가운데 청취 세션이 하나라도 있는 트랙 */
    public Set<Long> tracksWithListening(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(jdbc.queryForList(
                "select distinct track_id from listening_session where track_id in (:ids)",
                Map.of("ids", ids),
                Long.class));
    }

    public List<TimingRow> timings(long lessonId) {
        return jdbc.query(
                "select st.sentence_id, st.track_id, st.start_ms, st.end_ms, st.approximate from sentence_timing st"
                        + " join track t on t.id = st.track_id where t.lesson_id = :lesson",
                Map.of("lesson", lessonId),
                (rs, i) -> new TimingRow(
                        rs.getLong("sentence_id"),
                        rs.getLong("track_id"),
                        rs.getInt("start_ms"),
                        rs.getInt("end_ms"),
                        rs.getBoolean("approximate")));
    }

    public void deleteTimingsOfTracks(Collection<Long> trackIds) {
        if (!trackIds.isEmpty()) {
            jdbc.update("delete from sentence_timing where track_id in (:ids)", Map.of("ids", trackIds));
        }
    }

    public void deleteTimingsOfSentences(Collection<Long> sentenceIds) {
        if (!sentenceIds.isEmpty()) {
            jdbc.update("delete from sentence_timing where sentence_id in (:ids)", Map.of("ids", sentenceIds));
        }
    }

    public void insertTimings(List<TimingRow> rows) {
        if (rows.isEmpty()) {
            return;
        }
        jdbc.batchUpdate(
                "insert into sentence_timing (sentence_id, track_id, start_ms, end_ms, approximate)"
                        + " values (:sentence, :track, :start, :end, :approximate)",
                rows.stream()
                        .map(row -> Map.<String, Object>of(
                                "sentence", row.sentenceId(),
                                "track", row.trackId(),
                                "start", row.startMs(),
                                "end", row.endMs(),
                                "approximate", row.approximate()))
                        .toArray(Map[]::new));
    }

    public List<WordRow> words(long lessonId) {
        return jdbc.query(
                "select id, passage_id, sort_order, english, korean, pos, example_en, example_ko, moe_level, track_id,"
                        + " example_track_id from word where lesson_id = :lesson",
                Map.of("lesson", lessonId),
                (rs, i) -> new WordRow(
                        rs.getLong("id"),
                        rs.getObject("passage_id", Long.class),
                        rs.getInt("sort_order"),
                        rs.getString("english"),
                        rs.getString("korean"),
                        rs.getString("pos"),
                        rs.getString("example_en"),
                        rs.getString("example_ko"),
                        rs.getObject("moe_level", Integer.class),
                        rs.getObject("track_id", Long.class),
                        rs.getObject("example_track_id", Long.class)));
    }

    /** 지문 연결·음원 없이 넣는다(음원은 트랙을 만든 뒤 {@link #updateWord}로) */
    public long insertWord(
            long lessonId,
            int sortOrder,
            String english,
            String korean,
            String pos,
            String exampleEn,
            String exampleKo) {
        return insert(
                "insert into word (lesson_id, passage_id, sort_order, english, korean, pos, example_en, example_ko)"
                        + " values (:lesson, null, :sortOrder, :english, :korean, :pos, :exampleEn, :exampleKo)",
                params(
                        "lesson", lessonId,
                        "sortOrder", sortOrder,
                        "english", english,
                        "korean", korean,
                        "pos", pos,
                        "exampleEn", exampleEn,
                        "exampleKo", exampleKo));
    }

    /** 회차 어휘로 고친다: passage_id·moe_level은 NULL */
    public void updateWord(
            long id,
            String english,
            String korean,
            String pos,
            String exampleEn,
            String exampleKo,
            Long trackId,
            Long exampleTrackId) {
        jdbc.update(
                "update word set passage_id = null, moe_level = null, english = :english, korean = :korean, pos = :pos,"
                        + " example_en = :exampleEn, example_ko = :exampleKo, track_id = :track,"
                        + " example_track_id = :exampleTrack where id = :id",
                params(
                        "id", id,
                        "english", english,
                        "korean", korean,
                        "pos", pos,
                        "exampleEn", exampleEn,
                        "exampleKo", exampleKo,
                        "track", trackId,
                        "exampleTrack", exampleTrackId));
    }

    public void deleteWords(Collection<Long> ids) {
        if (!ids.isEmpty()) {
            jdbc.update("delete from word where id in (:ids)", Map.of("ids", ids));
        }
    }

    /** 지울 지문·트랙을 가리키는 단어의 연결을 먼저 끊는다(외래 키) */
    public void detachWords(Collection<Long> passageIds, Collection<Long> trackIds) {
        if (!passageIds.isEmpty()) {
            jdbc.update("update word set passage_id = null where passage_id in (:ids)", Map.of("ids", passageIds));
        }
        if (!trackIds.isEmpty()) {
            jdbc.update("update word set track_id = null where track_id in (:ids)", Map.of("ids", trackIds));
            jdbc.update(
                    "update word set example_track_id = null where example_track_id in (:ids)",
                    Map.of("ids", trackIds));
        }
    }

    private long insert(String sql, Map<String, ?> params) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(sql, new MapSqlParameterSource(params), keys, new String[] {"id"});
        return Objects.requireNonNull(keys.getKey(), "생성된 키 없음").longValue();
    }

    /** null 값을 허용하는 Map.of 대신 */
    private static Map<String, Object> params(Object... pairs) {
        Map<String, Object> map = new HashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put((String) pairs[i], pairs[i + 1]);
        }
        return map;
    }
}
