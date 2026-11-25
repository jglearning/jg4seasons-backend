package com.jglearning.jg4seasons.talk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jglearning.jg4seasons.IntegrationTest;
import com.jglearning.jg4seasons.TestData;
import java.util.Map;
import java.util.UUID;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** V15 프리토킹 표의 DB 제약: 값 목록(CHECK, 대소문자 구분)과 겹침 금지(UNIQUE) */
@IntegrationTest
@Transactional
class TalkSchemaTest {

    @Autowired
    JdbcTemplate jdbc;

    long studentId;
    long lessonId;

    @BeforeEach
    void setUp() {
        TestData data = new TestData(jdbc);
        studentId = data.student(data.academy("대화 학원"), "학생");
        lessonId = data.lessonId("L2-B1-01");
    }

    @Test
    void 세션을_만들면_안_쓰는_칸은_비어_있다() {
        long id = session(UUID.randomUUID().toString(), "lesson", "female", "created");

        Map<String, Object> row = jdbc.queryForMap(
                "select lesson_id, level_code, max_seconds, duration_sec, student_turn_count, usage_json, summary_json,"
                        + " started_at, ended_at from talk_session where id = ?",
                id);
        assertThat(row.get("lesson_id")).isEqualTo(lessonId);
        assertThat(row.get("level_code")).isEqualTo("L2");
        assertThat(row.get("duration_sec")).isNull();
        assertThat(row.get("student_turn_count")).isNull();
        assertThat(row.get("usage_json")).isNull();
        assertThat(row.get("summary_json")).isNull();
        assertThat(row.get("started_at")).isNull();
        assertThat(row.get("ended_at")).isNull();
    }

    @Test
    void 상태는_네_값만_받는다() {
        for (String status : new String[] {"created", "ended", "expired", "failed"}) {
            session(UUID.randomUUID().toString(), "day", "male", status);
        }

        violates(() -> session(UUID.randomUUID().toString(), "day", "male", "done"), "ck_talk_session_status");
        violates(() -> session(UUID.randomUUID().toString(), "day", "male", "Created"), "ck_talk_session_status");
    }

    @Test
    void 음성과_주제는_정한_값만_받는다() {
        violates(() -> session(UUID.randomUUID().toString(), "day", "FEMALE", "created"), "ck_talk_session_voice");
        violates(() -> session(UUID.randomUUID().toString(), "weather", "female", "created"), "ck_talk_session_topic");
        violates(() -> session(UUID.randomUUID().toString(), "Likes", "female", "created"), "ck_talk_session_topic");
    }

    @Test
    void 같은_client_id는_한_번만() {
        String clientId = UUID.randomUUID().toString();
        session(clientId, "day", "female", "created");

        violates(() -> session(clientId, "likes", "male", "created"), "uk_talk_session_client");
    }

    @Test
    void 대화_줄은_역할_두_값만_받고_순번이_겹치지_않는다() {
        long id = session(UUID.randomUUID().toString(), "day", "female", "ended");
        turn(id, 1, "character", "Hi!");
        turn(id, 2, "student", "Hello.");

        violates(() -> turn(id, 3, "teacher", "?"), "ck_talk_turn_role");
        violates(() -> turn(id, 4, "Student", "?"), "ck_talk_turn_role");
        violates(() -> turn(id, 2, "student", "again"), "uk_talk_turn_session_seq");
    }

    private long session(String clientId, String topic, String voice, String status) {
        jdbc.update(
                "insert into talk_session (client_session_id, student_id, lesson_id, level_code, topic, voice, status,"
                        + " max_seconds, model, prompt_version) values (?, ?, ?, 'L2', ?, ?, ?, 180, 'm', 'talk/v1')",
                clientId,
                studentId,
                lessonId,
                topic,
                voice,
                status);
        return jdbc.queryForObject("select last_insert_id()", Long.class);
    }

    private void turn(long sessionId, int seq, String role, String text) {
        jdbc.update(
                "insert into talk_turn (session_id, seq, role, text) values (?, ?, ?, ?)", sessionId, seq, role, text);
    }

    private static void violates(ThrowingCallable insert, String constraint) {
        assertThatThrownBy(insert).isInstanceOf(DataAccessException.class).hasMessageContaining(constraint);
    }
}
