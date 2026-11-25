package com.jglearning.jg4seasons.talk.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.jglearning.jg4seasons.IntegrationTest;
import com.jglearning.jg4seasons.TestData;
import com.jglearning.jg4seasons.talk.TalkEndReason;
import com.jglearning.jg4seasons.talk.TalkRole;
import com.jglearning.jg4seasons.talk.TalkSessionStatus;
import com.jglearning.jg4seasons.talk.TalkTopic;
import com.jglearning.jg4seasons.talk.TalkTranscript;
import com.jglearning.jg4seasons.talk.TalkTranscript.RawTurn;
import com.jglearning.jg4seasons.talk.TalkTranscript.Trimmed;
import com.jglearning.jg4seasons.talk.TalkVoice;
import com.jglearning.jg4seasons.talk.persistence.TalkSessionStore.NewTalkSession;
import com.jglearning.jg4seasons.talk.persistence.TalkSessionStore.TalkSessionRow;
import com.jglearning.jg4seasons.talk.persistence.TalkSessionStore.TalkSessionView;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** 세션 행 읽기: NULL 칸(회차 없음)이 그대로 null로 */
@IntegrationTest
@Transactional
class TalkSessionStoreTest {

    static final Instant NOW = Instant.parse("2026-11-26T03:00:00Z");

    @Autowired
    TalkSessionStore store;

    @Autowired
    JdbcTemplate jdbc;

    TestData data;
    long studentId;

    @BeforeEach
    void setUp() {
        data = new TestData(jdbc);
        studentId = data.student(data.academy("저장 학원"), "학생");
    }

    @Test
    void 회차가_없는_세션은_lessonId가_null() {
        String clientId = UUID.randomUUID().toString();
        store.insert(session(clientId, null), NOW);

        TalkSessionRow row = store.findByClientId(clientId).orElseThrow();

        assertThat(row.lessonId()).isNull();
        assertThat(row.studentId()).isEqualTo(studentId);
        assertThat(row.createdAt()).isEqualTo(NOW);
        // 문자열 변환은 저장소 경계에서만 — 읽으면 enum(#76 리뷰 2)
        assertThat(row.topic()).isEqualTo(TalkTopic.DAY);
        assertThat(row.voice()).isEqualTo(TalkVoice.FEMALE);
        assertThat(row.status()).isEqualTo(TalkSessionStatus.CREATED);
    }

    @Test
    void 회차가_있는_세션은_그_회차() {
        String clientId = UUID.randomUUID().toString();
        long lessonId = data.lessonId("L2-B1-03");
        store.insert(session(clientId, lessonId), NOW);

        assertThat(store.findByClientId(clientId).orElseThrow().lessonId()).isEqualTo(lessonId);
    }

    @Test
    void 본인_세션만_잠금_읽기로_가져온다() {
        String clientId = UUID.randomUUID().toString();
        long id = store.insert(session(clientId, null), NOW);
        long other = data.student(data.academy("다른 학원"), "다른 학생");

        assertThat(store.lockOwned(id, studentId)).isPresent();
        assertThat(store.lockOwned(id, other)).isEmpty();
        assertThat(store.lockOwned(-1, studentId)).isEmpty();
    }

    @Test
    void 종료를_저장하면_칸과_글자_기록이_남는다() {
        long id = store.insert(session(UUID.randomUUID().toString(), null), NOW);
        Trimmed trimmed = TalkTranscript.trim(
                List.of(new RawTurn(TalkRole.CHARACTER, "Hi!"), new RawTurn(TalkRole.STUDENT, "Hello.")), 168, 180);

        store.markEnded(id, trimmed, TalkEndReason.TIMEOUT, "{\"total_tokens\": 253}", NOW.plusSeconds(170));
        store.insertTurns(id, trimmed.turns());

        Map<String, Object> row = jdbc.queryForMap(
                "select status, duration_sec, student_turn_count, end_reason, usage_json, ended_at from talk_session"
                        + " where id = ?",
                id);
        assertThat(row.get("status")).isEqualTo("ended");
        assertThat(((Number) row.get("duration_sec")).intValue()).isEqualTo(168);
        assertThat(((Number) row.get("student_turn_count")).intValue()).isEqualTo(1);
        assertThat(row.get("end_reason")).isEqualTo("timeout");
        assertThat(String.valueOf(row.get("usage_json"))).contains("253");
        assertThat(row.get("ended_at")).isNotNull();
        assertThat(jdbc.queryForList(
                        "select concat(seq, ':', role, ':', text) from talk_turn where session_id = ? order by seq",
                        String.class,
                        id))
                .containsExactly("1:character:Hi!", "2:student:Hello.");

        TalkSessionView view = store.findOwned(id, studentId).orElseThrow();
        assertThat(view.status()).isEqualTo(TalkSessionStatus.ENDED);
        assertThat(view.durationSec()).isEqualTo(168);
        assertThat(view.studentTurnCount()).isEqualTo(1);
        assertThat(view.summaryJson()).isNull();
    }

    @Test
    void 요약을_저장하고_본인만_조회한다() {
        long id = store.insert(session(UUID.randomUUID().toString(), null), NOW);
        store.saveSummary(id, "{\"goodExpressions\": [\"play soccer\"], \"corrections\": []}");

        assertThat(store.findOwned(id, studentId).orElseThrow().summaryJson()).contains("play soccer");
        assertThat(store.findOwned(id, studentId + 100_000).isEmpty()).isTrue();
        assertThat(store.findOwned(id, studentId).orElseThrow().durationSec()).isNull();
    }

    @Test
    void 새_세션은_created나_failed로만_만든다() {
        // 리뷰 8: 종료 기록 없는 ended 행이 생기지 않게
        NewTalkSession created = session(UUID.randomUUID().toString(), null);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> created.withStatus(TalkSessionStatus.ENDED))
                .isInstanceOf(IllegalArgumentException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> created.withStatus(TalkSessionStatus.EXPIRED))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(created.withStatus(TalkSessionStatus.FAILED).status()).isEqualTo(TalkSessionStatus.FAILED);
    }

    private NewTalkSession session(String clientId, Long lessonId) {
        return new NewTalkSession(
                clientId,
                studentId,
                lessonId,
                "L2",
                TalkTopic.DAY,
                TalkVoice.FEMALE,
                TalkSessionStatus.CREATED,
                180,
                "m",
                "talk/v1");
    }
}
