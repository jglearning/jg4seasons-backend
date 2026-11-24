package com.jglearning.jg4seasons.content.persistence;

import com.jglearning.jg4seasons.content.api.WordTestItem;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/** 시험지 문항 읽기(콘텐츠 표, 테넌트 무관) */
@Component
public class WordTestItemStore {

    private final NamedParameterJdbcTemplate jdbc;

    public WordTestItemStore(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<WordTestItem> items(long lessonId, String section) {
        Map<String, Object> params = Map.of("lesson", lessonId, "section", section);
        Map<Long, List<String>> accepted = new HashMap<>();
        jdbc.query(
                "select a.item_id, a.text from word_test_accepted_answer a join word_test_item i on i.id = a.item_id"
                        + " where i.lesson_id = :lesson and i.section = :section order by a.id",
                params,
                rs -> {
                    accepted.computeIfAbsent(rs.getLong("item_id"), k -> new ArrayList<>())
                            .add(rs.getString("text"));
                });
        return jdbc.query(
                "select id, no, prompt, answer, hint from word_test_item where lesson_id = :lesson and section = :section"
                        + " order by no",
                params,
                (rs, i) -> new WordTestItem(
                        rs.getLong("id"),
                        rs.getInt("no"),
                        rs.getString("prompt"),
                        rs.getString("answer"),
                        rs.getString("hint"),
                        accepted.getOrDefault(rs.getLong("id"), List.of())));
    }

    public int count(long lessonId, String section) {
        Integer count = jdbc.queryForObject(
                "select count(*) from word_test_item where lesson_id = :lesson and section = :section",
                Map.of("lesson", lessonId, "section", section),
                Integer.class);
        return count == null ? 0 : count;
    }
}
