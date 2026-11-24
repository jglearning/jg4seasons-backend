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

/** 학원별 단어시험 켜기: settings JSON의 features.wordTest가 JSON true일 때만 켜짐. 기본은 꺼짐 */
@IntegrationTest
@Transactional
class AcademyWordTestFeatureTest {

    @Autowired
    AcademyDirectory academies;

    @Autowired
    JdbcTemplate jdbc;

    long academyId;

    @BeforeEach
    void setUp() {
        academyId = new TestData(jdbc).academy("시험 학원");
    }

    @Test
    void features_wordTest가_true면_켜진다() {
        settings("{\"timezone\":\"Asia/Seoul\",\"features\":{\"wordTest\":true}}");

        assertThat(academies.wordTestEnabled(academyId)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{\"timezone\":\"Asia/Seoul\"}",
                "{\"features\":null}",
                "{\"features\":{}}",
                "{\"features\":{\"wordTest\":false}}",
                "{\"features\":{\"wordTest\":null}}",
            })
    void 키가_없거나_null_false면_꺼져_있다(String json) {
        settings(json);

        assertThat(academies.wordTestEnabled(academyId)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{\"features\":{\"wordTest\":\"true\"}}",
                "{\"features\":{\"wordTest\":1}}",
                "{\"features\":true}",
                "{\"features\":[\"wordTest\"]}",
                "[]",
            })
    void 형식이_틀리면_조용히_끄지_않고_데이터_오류(String json) {
        // 운영은 SQL로 직접 켠다 — 문자열 "true" 같은 입력 실수가 "켰는데 안 보인다"로 묻히지 않게(500 + ERROR 로그)
        settings(json);

        assertThatThrownBy(() -> academies.wordTestEnabled(academyId)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void settings가_없으면_꺼져_있다() {
        jdbc.update("update academy set settings = null where id = ?", academyId);

        assertThat(academies.wordTestEnabled(academyId)).isFalse();
    }

    @Test
    void 없는_학원은_데이터_오류() {
        assertThatThrownBy(() -> academies.wordTestEnabled(-1)).isInstanceOf(IllegalStateException.class);
    }

    private void settings(String json) {
        jdbc.update("update academy set settings = ? where id = ?", json, academyId);
    }
}
