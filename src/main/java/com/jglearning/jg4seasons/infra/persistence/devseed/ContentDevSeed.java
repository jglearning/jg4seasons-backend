package com.jglearning.jg4seasons.infra.persistence.devseed;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 로컬 확인용 회차 내용: 데모 반 권(L2-B1)의 회차 1~24마다 지문 6 × 문장 3. 문장은 직접 지은 문장이다 — 교재 지문은 공개 저장소에 넣지 않는다(실제 데이터는 콘텐츠
 * import 스크립트가 DB에만 넣는다). 음원 파일은 없고 경로(file_key)만 있다. 레벨의 속도마다 지문 트랙을 만들고, 1.0은 문장 3000ms·간격 500ms로 이어 붙인
 * 정확한 시각, 다른 속도는 1.0 시각을 길이 비율로 늘이거나 줄인 근사(approximate). 이미 1회차 지문이 있으면 아무것도 하지 않는다.
 */
public class ContentDevSeed {

    static final String BOOK_CODE = DevSeed.BOOK_CODE;
    static final int PASSAGES = 6;
    static final int SENTENCES = 3;
    static final int SENTENCE_MS = 3000;
    static final int GAP_MS = 500;

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String[] SUBJECTS = {
        "A curious fox", "Our class", "The old bridge", "A quiet river", "My friend", "The city bus"
    };
    private static final String[] ACTIONS = {
        "wakes up early and looks around.", "talks about a small plan.", "waits for the rain to stop."
    };

    private final JdbcTemplate jdbc;

    public ContentDevSeed(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** @return 새로 넣었으면 true, 이미 있어서 건너뛰었으면 false */
    public boolean seed() {
        Integer existing = jdbc.queryForObject(
                "select count(*) from passage p join lesson l on l.id = p.lesson_id where l.code = ?",
                Integer.class,
                BOOK_CODE + "-01");
        if (existing != null && existing > 0) {
            return false;
        }
        List<String> speeds = speeds();
        List<LessonRef> lessons = jdbc.query(
                "select l.id, l.code, l.number from lesson l join book b on b.id = l.book_id"
                        + " where b.code = ? order by l.number",
                (rs, i) -> new LessonRef(rs.getLong("id"), rs.getString("code"), rs.getInt("number")),
                BOOK_CODE);
        for (LessonRef lesson : lessons) {
            for (int p = 1; p <= PASSAGES; p++) {
                seedPassage(lesson, p, speeds);
            }
        }
        return true;
    }

    private record LessonRef(long id, String code, int number) {}

    /** 그 권 레벨의 passage_speeds(예: ["0.8","1.0","1.2"]) */
    private List<String> speeds() {
        String json = jdbc.queryForObject(
                "select v.passage_speeds from level v join book b on b.level_id = v.id where b.code = ?",
                String.class,
                BOOK_CODE);
        List<String> speeds = new ArrayList<>();
        for (JsonNode speed : JSON.readTree(json)) {
            speeds.add(speed.asString());
        }
        return speeds;
    }

    private void seedPassage(LessonRef lesson, int p, List<String> speeds) {
        long passageId = insert(
                "insert into passage (lesson_id, no, title, word_count) values (?, ?, ?, ?)",
                lesson.id(),
                p,
                "Day " + lesson.number() + " Practice Passage " + p,
                30);
        List<Long> sentenceIds = new ArrayList<>();
        for (int s = 1; s <= SENTENCES; s++) {
            sentenceIds.add(insert(
                    "insert into sentence (passage_id, no, text) values (?, ?, ?)",
                    passageId,
                    s,
                    SUBJECTS[(lesson.number() + p + s) % SUBJECTS.length] + " " + ACTIONS[s - 1]));
        }
        int oneSpeedMs = SENTENCES * SENTENCE_MS + (SENTENCES - 1) * GAP_MS;
        for (int i = 0; i < speeds.size(); i++) {
            String speed = speeds.get(i);
            double ratio = 1.0 / Double.parseDouble(speed);
            boolean approximate = !"1.0".equals(speed);
            long trackId = insert(
                    "insert into track (lesson_id, passage_id, kind, variant, file_key, duration_ms, sort_order)"
                            + " values (?, ?, 'passage', ?, ?, ?, ?)",
                    lesson.id(),
                    passageId,
                    "p" + p + ":" + speed,
                    "audio/" + lesson.code() + "/" + lesson.code() + "_p" + p + "_" + speed + ".mp3",
                    (int) Math.round(oneSpeedMs * ratio),
                    p * 10 + i);
            for (int s = 0; s < SENTENCES; s++) {
                int start = s * (SENTENCE_MS + GAP_MS);
                jdbc.update(
                        "insert into sentence_timing (sentence_id, track_id, start_ms, end_ms, approximate)"
                                + " values (?, ?, ?, ?, ?)",
                        sentenceIds.get(s),
                        trackId,
                        (int) Math.round(start * ratio),
                        (int) Math.round((start + SENTENCE_MS) * ratio),
                        approximate);
            }
        }
    }

    private long insert(String sql, Object... args) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(
                connection -> {
                    PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
                    for (int i = 0; i < args.length; i++) {
                        statement.setObject(i + 1, args[i]);
                    }
                    return statement;
                },
                keys);
        return Objects.requireNonNull(keys.getKey(), "생성된 키 없음").longValue();
    }
}
