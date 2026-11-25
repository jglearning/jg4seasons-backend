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
            "user_setting",
            "class",
            "class_teacher",
            "enrollment",
            "class_session",
            "book_license",
            // 학습 기록
            "listening_session",
            "listening_segment",
            "track_progress",
            "lesson_progress",
            "passage_progress",
            "word_study",
            // 단어시험 사진 채점(V12) — 문항 정의는 공용 콘텐츠, 나머지는 학습 기록
            "word_test_item",
            "word_test_accepted_answer",
            "word_test_submission",
            "word_test_answer",
            "grading_job",
            // 캐릭터 프리토킹(V15) — 세션과 글자 기록
            "talk_session",
            "talk_turn",
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
            List.of("book_license", "academy_id,book_id"),
            List.of("listening_session", "client_session_id"),
            List.of("refresh_token", "token_hash"),
            List.of("word_test_item", "lesson_id,section,no"),
            List.of("word_test_accepted_answer", "item_id,text"),
            List.of("word_test_submission", "client_submission_id"),
            List.of("word_test_submission", "student_id,lesson_id,section,attempt_no"),
            List.of("word_test_answer", "submission_id,item_id"),
            List.of("grading_job", "submission_id"),
            List.of("talk_session", "client_session_id"),
            List.of("talk_turn", "session_id,seq"));

    @Autowired
    JdbcTemplate jdbc;

    private Set<String> tables() {
        return Set.copyOf(jdbc.queryForList(
                "select table_name from information_schema.tables"
                        + " where table_schema = database() and table_name <> 'flyway_schema_history'",
                String.class));
    }

    @Test
    void 테이블은_ERD의_1차_29개와_단어시험_5개_프리토킹_2개다() {
        assertThat(FIRST_RELEASE_TABLES).hasSize(36);
        assertThat(tables()).containsExactlyInAnyOrderElementsOf(FIRST_RELEASE_TABLES);
    }

    @Test
    void user_setting_표는_user_id가_키이고_voice는_비어_있을_수_있다() {
        // V11(ADR 0035). 학생 1명당 1행. 학원 칸은 없다 — 읽는 쪽이 user를 조인해 학원 조건을 넣는다
        assertThat(jdbc.queryForList(
                        "select column_name from information_schema.key_column_usage"
                                + " where table_schema = database() and table_name = 'user_setting'"
                                + " and constraint_name = 'PRIMARY' order by ordinal_position",
                        String.class))
                .containsExactly("user_id");
        assertThat(jdbc.queryForList(
                        "select column_name from information_schema.columns"
                                + " where table_schema = database() and table_name = 'user_setting'",
                        String.class))
                .containsExactlyInAnyOrder("user_id", "voice", "created_at", "updated_at");
        assertThat(isNullable("user_setting", "voice")).isEqualTo("YES");
        assertThat(columnType("user_setting", "voice")).isEqualTo("varchar(8)");
        assertThat(jdbc.queryForObject(
                        "select collation_name from information_schema.columns"
                                + " where table_schema = database() and table_name = 'user_setting'"
                                + " and column_name = 'voice'",
                        String.class))
                .as("글자 그대로 비교 — 기본 정렬(ai_ci)이면 CHECK가 대소문자를 구분하지 못한다")
                .isEqualTo("utf8mb4_0900_bin");
        assertThat(jdbc.queryForObject(
                        "select count(*) from information_schema.table_constraints"
                                + " where table_schema = database() and table_name = 'user_setting'"
                                + " and constraint_type = 'CHECK'",
                        Integer.class))
                .as("목소리 값 제약")
                .isEqualTo(1);
    }

    @Test
    void 지문_집계_표는_학생과_지문이_키이고_기준_트랙과_기준_길이를_가진다() {
        // V10(ADR-34). 학원 칸은 없다(다른 집계 표와 같음 — 읽는 api는 학원 학생 묶음만 받는다)
        assertThat(jdbc.queryForList(
                        "select column_name from information_schema.key_column_usage"
                                + " where table_schema = database() and table_name = 'passage_progress'"
                                + " and constraint_name = 'PRIMARY' order by ordinal_position",
                        String.class))
                .containsExactly("student_id", "passage_id");
        assertThat(jdbc.queryForList(
                        "select column_name from information_schema.columns"
                                + " where table_schema = database() and table_name = 'passage_progress'",
                        String.class))
                .containsExactlyInAnyOrder(
                        "student_id",
                        "passage_id",
                        "ref_track_id",
                        "ref_duration_ms",
                        "merged_ranges",
                        "covered_ms",
                        "coverage",
                        "completed_at",
                        "created_at",
                        "updated_at");
        assertThat(isNullable("passage_progress", "ref_track_id")).isEqualTo("NO");
        assertThat(isNullable("passage_progress", "ref_duration_ms")).isEqualTo("NO");
        assertThat(isNullable("passage_progress", "completed_at")).isEqualTo("YES");
        assertThat(columnType("passage_progress", "coverage")).isEqualTo("decimal(5,4)");
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
