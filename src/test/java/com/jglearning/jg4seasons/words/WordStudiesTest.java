package com.jglearning.jg4seasons.words;

import static org.assertj.core.api.Assertions.assertThat;

import com.jglearning.jg4seasons.IntegrationTest;
import com.jglearning.jg4seasons.TestData;
import com.jglearning.jg4seasons.words.api.WordStudies;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** 회차 단어 학습 결과(word_study) 읽기. */
@IntegrationTest
@Transactional
class WordStudiesTest {

    @Autowired
    WordStudies wordStudies;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void 단어_학습_결과() {
        TestData data = new TestData(jdbc);
        long academyId = data.academy("테스트 학원");
        long studentId = data.student(academyId, "김학생");
        long otherId = data.student(academyId, "이학생");
        long lessonId = data.lessonId("L2-B1-05");
        jdbc.update(
                "insert into word_study (student_id, lesson_id, cards_viewed, mem_known, last_viewed_at)"
                        + " values (?, ?, 57, 40, '2026-11-05 10:00:00')",
                studentId,
                lessonId);

        assertThat(wordStudies.memKnown(studentId, lessonId)).isEqualTo(40);
        assertThat(wordStudies.memKnown(studentId, data.lessonId("L2-B1-04")))
                .as("같은 학생의 다른 회차")
                .isZero();
        assertThat(wordStudies.memKnown(otherId, lessonId)).as("기록 없음").isZero();
    }
}
