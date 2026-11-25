package com.jglearning.jg4seasons.academy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jglearning.jg4seasons.IntegrationTest;
import com.jglearning.jg4seasons.TestData;
import com.jglearning.jg4seasons.academy.api.AcademyDirectory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** 학원별 프리토킹 켜기(features.talk)와 구독 대신 값(features.talkEntitled). 단어시험과 같은 규칙: JSON true만 켜짐, 기본 꺼짐 */
@IntegrationTest
@Transactional
class AcademyTalkFeatureTest {

    @Autowired
    AcademyDirectory academies;

    @Autowired
    JdbcTemplate jdbc;

    long academyId;

    @BeforeEach
    void setUp() {
        academyId = new TestData(jdbc).academy("대화 학원");
    }

    // 한 테스트에서 settings는 한 번만 바꾼다 — 같은 트랜잭션에서 학원 엔티티를 한 번 읽으면 JDBC로 바꾼 값이 보이지 않는다

    @Test
    void 켜기만_하고_구독이_없으면_talk만_true() {
        settings("{\"features\":{\"talk\":true}}");

        assertThat(academies.talkEnabled(academyId)).isTrue();
        assertThat(academies.talkEntitled(academyId)).isFalse();
        assertThat(academies.wordTestEnabled(academyId)).isFalse();
    }

    @Test
    void 켜기와_구독이_모두_true() {
        settings("{\"features\":{\"talk\":true,\"talkEntitled\":true}}");

        assertThat(academies.talkEnabled(academyId)).isTrue();
        assertThat(academies.talkEntitled(academyId)).isTrue();
    }

    @Test
    void 단어시험만_켠_학원은_프리토킹이_꺼져_있다() {
        settings("{\"features\":{\"wordTest\":true}}");

        assertThat(academies.talkEnabled(academyId)).isFalse();
        assertThat(academies.wordTestEnabled(academyId)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{\"timezone\":\"Asia/Seoul\"}",
                "{\"features\":null}",
                "{\"features\":{}}",
                "{\"features\":{\"talk\":false,\"talkEntitled\":false}}",
                "{\"features\":{\"talk\":null,\"talkEntitled\":null}}",
            })
    void 키가_없거나_null_false면_꺼져_있다(String json) {
        settings(json);

        assertThat(academies.talkEnabled(academyId)).isFalse();
        assertThat(academies.talkEntitled(academyId)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"true\"", "1", "[]"})
    void 불리언이_아니면_데이터_오류(String value) {
        settings("{\"features\":{\"talk\":" + value + ",\"talkEntitled\":" + value + "}}");

        assertThatThrownBy(() -> academies.talkEnabled(academyId)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> academies.talkEntitled(academyId)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void settings가_없으면_꺼져_있다() {
        jdbc.update("update academy set settings = null where id = ?", academyId);

        assertThat(academies.talkEnabled(academyId)).isFalse();
        assertThat(academies.talkEntitled(academyId)).isFalse();
    }

    private void settings(String json) {
        jdbc.update("update academy set settings = ? where id = ?", json, academyId);
    }
}
