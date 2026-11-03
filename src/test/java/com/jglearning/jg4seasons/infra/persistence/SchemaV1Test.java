package com.jglearning.jg4seasons.infra.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.jglearning.jg4seasons.IntegrationTest;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** Flyway V1이 ERD v0.2의 1차 테이블을 그대로 만드는지. */
@IntegrationTest
class SchemaV1Test {

    static final Set<String> FIRST_RELEASE_TABLES = Set.of(
            // 공용 콘텐츠
            "level",
            "book",
            "lesson",
            "passage",
            "sentence",
            "grammar_item",
            "lesson_grammar_item",
            "track",
            "word",
            "completion_rule",
            // 학원
            "academy",
            "user",
            "auth_identity",
            "class",
            "class_teacher",
            "enrollment",
            "class_session",
            // 학습 기록
            "listening_session",
            "listening_segment",
            "track_progress",
            "lesson_progress",
            "cloze_attempt",
            "cloze_progress",
            "word_study",
            // 인증·운영
            "refresh_token",
            "audit_log",
            "daily_metric");

    /** 테이블 → UNIQUE 인덱스 컬럼 조합(순서대로). ERD §1~§5. */
    static final List<List<String>> REQUIRED_UNIQUES = List.of(
            List.of("level", "code"),
            List.of("book", "code"),
            List.of("book", "level_id,season"),
            List.of("lesson", "code"),
            List.of("lesson", "book_id,number"),
            List.of("passage", "lesson_id,no"),
            List.of("sentence", "passage_id,no"),
            List.of("sentence", "track_id"),
            List.of("track", "lesson_id,kind,variant"),
            List.of("track", "file_key"),
            List.of("word", "lesson_id,sort_order"),
            List.of("grammar_item", "code"),
            List.of("completion_rule", "level_id"),
            List.of("academy", "slug"),
            List.of("auth_identity", "user_id,provider"),
            List.of("auth_identity", "provider,identifier"),
            List.of("class", "access_code"),
            List.of("class_session", "class_id,seq"),
            List.of("listening_session", "client_session_id"),
            List.of("refresh_token", "token_hash"));

    @Autowired
    JdbcTemplate jdbc;

    private Set<String> tables() {
        return Set.copyOf(jdbc.queryForList(
                "select table_name from information_schema.tables"
                        + " where table_schema = database() and table_name <> 'flyway_schema_history'",
                String.class));
    }

    @Test
    void 테이블은_ERD의_1차_27개다() {
        assertThat(FIRST_RELEASE_TABLES).hasSize(27);
        assertThat(tables()).containsExactlyInAnyOrderElementsOf(FIRST_RELEASE_TABLES);
    }

    @Test
    void 모든_테이블에_created_at과_updated_at이_있다() {
        for (String table : FIRST_RELEASE_TABLES) {
            List<String> columns = jdbc.queryForList(
                    "select column_name from information_schema.columns"
                            + " where table_schema = database() and table_name = ?",
                    String.class,
                    table);
            assertThat(columns).as(table).contains("created_at", "updated_at");
        }
    }

    @Test
    void 주요_UNIQUE_제약이_있다() {
        Map<String, Set<String>> uniques = jdbc
                .queryForList("select table_name, index_name,"
                        + " group_concat(column_name order by seq_in_index) as cols"
                        + " from information_schema.statistics"
                        + " where table_schema = database() and non_unique = 0"
                        + " group by table_name, index_name")
                .stream()
                .collect(Collectors.groupingBy(
                        row -> (String) row.get("TABLE_NAME"),
                        Collectors.mapping(row -> (String) row.get("cols"), Collectors.toSet())));
        for (List<String> required : REQUIRED_UNIQUES) {
            assertThat(uniques.getOrDefault(required.get(0), Set.of()))
                    .as("%s UNIQUE(%s)", required.get(0), required.get(1))
                    .contains(required.get(1));
        }
    }

    @Test
    void 개인정보_컬럼이_없다() {
        List<String> found = jdbc.queryForList(
                "select concat(table_name, '.', column_name) from information_schema.columns"
                        + " where table_schema = database() and column_name regexp 'phone|tel|birth|email'",
                String.class);
        assertThat(found).as("학생 개인정보는 이름·학교·학년만").isEmpty();
    }

    @Test
    void 문자셋은_utf8mb4다() {
        List<String> collations = jdbc.queryForList(
                "select concat(table_name, ':', table_collation) from information_schema.tables"
                        + " where table_schema = database() and table_name <> 'flyway_schema_history'",
                String.class);
        assertThat(collations).allSatisfy(c -> assertThat(c).contains(":utf8mb4_"));
    }
}
