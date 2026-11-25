package com.jglearning.jg4seasons.talk.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.jglearning.jg4seasons.IntegrationTest;
import com.jglearning.jg4seasons.TestData;
import com.jglearning.jg4seasons.talk.TalkSessionStatus;
import com.jglearning.jg4seasons.talk.TalkTopic;
import com.jglearning.jg4seasons.talk.TalkVoice;
import com.jglearning.jg4seasons.talk.persistence.TalkSessionStore.NewTalkSession;
import com.jglearning.jg4seasons.talk.persistence.TalkSessionStore.TalkSessionRow;
import java.time.Instant;
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
