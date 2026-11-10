package com.jglearning.jg4seasons;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

/**
 * 통합 테스트용 데이터 넣기. 테스트 클래스는 @Transactional로 되돌리거나, 커밋이 필요하면 직접 지운다.
 *
 * <p>가입 코드·slug는 실행마다 겹치지 않게 만든다(테스트 컨텍스트와 DB를 공유한다).
 */
public final class TestData {

    private static final AtomicInteger SEQ = new AtomicInteger();

    private final JdbcTemplate jdbc;

    public TestData(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 영숫자 8자, 실행 안에서 겹치지 않음. 'T'로 시작해 개발 시드 코드(G7KM2XP4)와 겹치지 않는다 */
    public static String uniqueAccessCode() {
        StringBuilder code = new StringBuilder("T");
        for (int i = 0; i < 7; i++) {
            code.append(ALPHANUMERIC.charAt(ThreadLocalRandom.current().nextInt(ALPHANUMERIC.length())));
        }
        return code.toString();
    }

    private static final String ALPHANUMERIC = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    public long academy(String name) {
        return insert(
                "insert into academy (name, slug, status) values (?, ?, 'active')",
                name,
                "t" + SEQ.incrementAndGet() + "-" + System.nanoTime());
    }

    public long bookId(String code) {
        return Objects.requireNonNull(jdbc.queryForObject("select id from book where code = ?", Long.class, code));
    }

    public long clazz(long academyId, String bookCode, String name, String accessCode, String status) {
        return insert(
                "insert into `class` (academy_id, book_id, name, start_date, weekdays, access_code,"
                        + " code_issued_at, allow_future_lessons, status) values (?, ?, ?, ?, '0101000', ?, ?, false, ?)",
                academyId,
                bookId(bookCode),
                name,
                LocalDate.of(2026, 11, 1),
                accessCode,
                LocalDateTime.of(2026, 11, 1, 0, 0),
                status);
    }

    public long student(long academyId, String name) {
        return insert(
                "insert into `user` (academy_id, role, name, status) values (?, 'student', ?, 'active')",
                academyId,
                name);
    }

    /** 교사·관리자 사용자. role은 'teacher' 또는 'admin' */
    public long staff(long academyId, String name, String role) {
        return insert(
                "insert into `user` (academy_id, role, name, status) values (?, ?, ?, 'active')",
                academyId,
                role,
                name);
    }

    public void assignTeacher(long classId, long teacherId) {
        jdbc.update("insert into class_teacher (class_id, user_id) values (?, ?)", classId, teacherId);
    }

    public void enroll(long classId, long studentId) {
        jdbc.update(
                "insert into enrollment (class_id, student_id, joined_at) values (?, ?, ?)",
                classId,
                studentId,
                LocalDate.of(2026, 11, 1));
    }

    public long lessonId(String code) {
        return Objects.requireNonNull(jdbc.queryForObject("select id from lesson where code = ?", Long.class, code));
    }

    /** 진도표 한 행. status: scheduled · done · cancelled */
    public void session(long classId, int seq, String lessonCode, LocalDate date, String status) {
        jdbc.update(
                "insert into class_session (class_id, seq, lesson_id, scheduled_date, status) values (?, ?, ?, ?, ?)",
                classId,
                seq,
                lessonId(lessonCode),
                date,
                status);
    }

    /** 문장 길이와 문장 사이 간격. 첫 문장 시각 = [0, 3000] */
    public static final int SENTENCE_MS = 3000;

    public static final int GAP_MS = 500;

    /**
     * 회차 내용: 지문 passages개 × 문장 sentencesPerPassage개(직접 지은 문장 — 교재 지문 아님), 목소리마다 지문 트랙과 정확한 문장 시각(ADR-27). 목소리끼리
     * 길이가 같다. 반환: "p1:us_w" → trackId
     */
    public Map<String, Long> lessonContent(
            String lessonCode, int passages, int sentencesPerPassage, List<String> voices) {
        long lessonId = lessonId(lessonCode);
        Map<String, Long> tracks = new LinkedHashMap<>();
        for (int p = 1; p <= passages; p++) {
            long passageId = insert(
                    "insert into passage (lesson_id, no, title, word_count) values (?, ?, ?, ?)",
                    lessonId,
                    p,
                    "Made-up Passage " + p,
                    100 + p);
            List<Long> sentenceIds = new ArrayList<>();
            for (int s = 1; s <= sentencesPerPassage; s++) {
                sentenceIds.add(insert(
                        "insert into sentence (passage_id, no, text, speaker) values (?, ?, ?, ?)",
                        passageId,
                        s,
                        "Passage " + p + " sentence " + s + " is a made-up line.",
                        s == 2 ? "A" : null));
            }
            int passageMs = sentencesPerPassage * SENTENCE_MS + (sentencesPerPassage - 1) * GAP_MS;
            for (int i = 0; i < voices.size(); i++) {
                String voice = voices.get(i);
                long trackId = insert(
                        "insert into track (lesson_id, passage_id, kind, variant, file_key, duration_ms, sort_order)"
                                + " values (?, ?, 'passage', ?, ?, ?, ?)",
                        lessonId,
                        passageId,
                        "p" + p + ":" + voice,
                        "audio/" + lessonCode + "/" + lessonCode + "_p" + p + "_" + voice + ".mp3",
                        passageMs,
                        p * 10 + i);
                tracks.put("p" + p + ":" + voice, trackId);
                for (int s = 0; s < sentencesPerPassage; s++) {
                    int start = s * (SENTENCE_MS + GAP_MS);
                    jdbc.update(
                            "insert into sentence_timing (sentence_id, track_id, start_ms, end_ms) values (?, ?, ?, ?)",
                            sentenceIds.get(s),
                            trackId,
                            start,
                            start + SENTENCE_MS);
                }
            }
        }
        return tracks;
    }

    /** 회차 단어 count개(지문 1에 붙임). lessonContent 다음에 부른다 */
    public void words(String lessonCode, int count) {
        long lessonId = lessonId(lessonCode);
        long passageId = Objects.requireNonNull(
                jdbc.queryForObject("select id from passage where lesson_id = ? and no = 1", Long.class, lessonId));
        for (int i = 1; i <= count; i++) {
            jdbc.update(
                    "insert into word (lesson_id, passage_id, sort_order, english, korean, pos, example_en, example_ko)"
                            + " values (?, ?, ?, ?, ?, 'n', ?, ?)",
                    lessonId,
                    passageId,
                    i,
                    "word" + i,
                    "단어" + i,
                    "A made-up example " + i + ".",
                    "지어낸 예문 " + i);
        }
    }

    public void leave(long classId, long studentId) {
        jdbc.update(
                "update enrollment set left_at = ? where class_id = ? and student_id = ?",
                LocalDate.of(2026, 11, 2),
                classId,
                studentId);
    }

    private long insert(String sql, Object... args) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(
                connection -> {
                    PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
                    for (int i = 0; i < args.length; i++) {
                        ps.setObject(i + 1, args[i]);
                    }
                    return ps;
                },
                keys);
        return Objects.requireNonNull(keys.getKey()).longValue();
    }
}
