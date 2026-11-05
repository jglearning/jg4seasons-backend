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

/** Flyway 전체(V1~)를 적용한 스키마가 ERD의 1차 테이블과 같은지(마이그레이션이 늘면 함께 갱신). */
@IntegrationTest
class SchemaTest {

    static final Set<String> FIRST_RELEASE_TABLES = Set.of(
            // 공용 콘텐츠
            "level",
            "book",
            "lesson",
            "passage",
            "sentence",
            "sentence_timing",
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
            List.of("sentence_timing", "sentence_id,track_id"),
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
    void 테이블은_ERD의_1차_26개다() {
        assertThat(FIRST_RELEASE_TABLES).hasSize(26);
        assertThat(tables()).containsExactlyInAnyOrderElementsOf(FIRST_RELEASE_TABLES);
    }

    @Test
    void 빈칸_칸과_문장_트랙_종류가_없다() {
        List<String> found = jdbc.queryForList(
                "select concat(table_name, '.', column_name) from information_schema.columns"
                        + " where table_schema = database() and ("
                        + " (table_name = 'lesson_progress' and column_name in ('cloze_total', 'cloze_correct'))"
                        + " or (table_name = 'completion_rule' and column_name in ('cloze_required_ratio', 'cloze_hint'))"
                        + " or (table_name = 'sentence'"
                        + " and column_name in ('text_before', 'blank_answer', 'text_after', 'track_id')))",
                String.class);
        assertThat(found).as("ADR-26으로 없앤 칸").isEmpty();
        assertThat(columnType("track", "kind")).isEqualTo("enum('passage','word','word_example')");
    }

    @Test
    void 트랙_칸을_다시_정의해도_NOT_NULL이_남는다() {
        // MODIFY는 칸 정의를 통째로 다시 쓴다 — NOT NULL을 빠뜨리면 조용히 NULL 허용이 된다
        assertThat(isNullable("track", "kind")).isEqualTo("NO");
        assertThat(isNullable("track", "variant")).isEqualTo("NO");
        assertThat(columnType("track", "variant")).isEqualTo("varchar(12)");
    }

    @Test
    void 문장은_text_하나다() {
        assertThat(columnType("sentence", "text")).isEqualTo("varchar(800)");
        assertThat(isNullable("sentence", "text")).isEqualTo("NO");
    }

    @Test
    void 문장_시각은_문장과_지문_트랙을_가리킨다() {
        List<String> foreignKeys = jdbc.queryForList(
                "select concat(column_name, '->', referenced_table_name, '.', referenced_column_name)"
                        + " from information_schema.key_column_usage"
                        + " where table_schema = database() and table_name = 'sentence_timing'"
                        + " and referenced_table_name is not null",
                String.class);
        assertThat(foreignKeys).containsExactlyInAnyOrder("sentence_id->sentence.id", "track_id->track.id");

        List<String> trackFirstIndexes = jdbc.queryForList(
                "select index_name from information_schema.statistics"
                        + " where table_schema = database() and table_name = 'sentence_timing'"
                        + " and column_name = 'track_id' and seq_in_index = 1",
                String.class);
        assertThat(trackFirstIndexes).as("track_id로 찾는 인덱스").isNotEmpty();

        String check = jdbc.queryForObject(
                "select check_clause from information_schema.check_constraints"
                        + " where constraint_schema = database() and constraint_name = 'ck_sentence_timing_range'",
                String.class);
        assertThat(check).isEqualTo("((`start_ms` >= 0) and (`end_ms` > `start_ms`))");
    }

    private String isNullable(String table, String column) {
        return jdbc.queryForObject(
                "select is_nullable from information_schema.columns"
                        + " where table_schema = database() and table_name = ? and column_name = ?",
                String.class,
                table,
                column);
    }

    private String columnType(String table, String column) {
        return jdbc.queryForObject(
                "select column_type from information_schema.columns"
                        + " where table_schema = database() and table_name = ? and column_name = ?",
                String.class,
                table,
                column);
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
