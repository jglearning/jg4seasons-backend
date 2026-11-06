package com.jglearning.jg4seasons.words.persistence;

import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** word_study 읽기. academy_id 칸이 없어, 호출자가 학생의 학원 소속을 먼저 확인한다. */
@Component
public class WordStudyStore {

    private final JdbcTemplate jdbc;

    public WordStudyStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Integer> memKnown(long studentId, long lessonId) {
        return jdbc
                .query(
                        "select mem_known from word_study where student_id = ? and lesson_id = ?",
                        (rs, i) -> rs.getInt("mem_known"),
                        studentId,
                        lessonId)
                .stream()
                .findFirst();
    }
}
