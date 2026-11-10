package com.jglearning.jg4seasons.infra.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.jglearning.jg4seasons.IntegrationTest;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** V2 기준 데이터: 레벨·권·회차·완료 규칙. 값의 출처는 설명회 자료 v0.28, 01 기능명세 §7.2, 05 디자인가이드 §2.1. */
@IntegrationTest
class ReferenceDataTest {

    @Autowired
    JdbcTemplate jdbc;

    private Map<String, Object> lesson(String code) {
        return jdbc.queryForMap("select number, module, type, title, passage_count from lesson where code = ?", code);
    }

    private String bookTitle(String code) {
        return jdbc.queryForObject("select title from book where code = ?", String.class, code);
    }

    @Test
    void 레벨은_5개다() {
        assertThat(jdbc.queryForList("select code from level order by sort_order", String.class))
                .containsExactly("L1", "L2", "L3", "L4", "K");
        assertThat(jdbc.queryForObject("select name_ko from level where code = 'L2'", String.class))
                .isEqualTo("그린");
        assertThat(jdbc.queryForObject("select name_ko from level where code = 'K'", String.class))
                .isEqualTo("블랙");
    }

    @Test
    void 레벨_색은_디자인가이드_값이다() {
        Map<String, String> colors =
                Map.of("L1", "#0F8A74", "L2", "#1F7A4F", "L3", "#2563EB", "L4", "#1E3A8A", "K", "#111827");
        colors.forEach((code, hex) -> assertThat(
                        jdbc.queryForObject("select color_hex from level where code = ?", String.class, code))
                .as(code)
                .isEqualTo(hex));
    }

    @Test
    void 레벨마다_지문_목소리는_넷() {
        for (String level : new String[] {"L1", "L2", "L3", "L4", "K"}) {
            assertThat(voicesEqual(level, "[\"us_w\",\"us_m\",\"uk_w\",\"uk_m\"]"))
                    .as(level)
                    .isTrue();
        }
    }

    private boolean voicesEqual(String level, String json) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "select passage_voices = cast(? as json) from level where code = ?", Boolean.class, json, level));
    }

    @Test
    void 책은_17개다() {
        assertThat(jdbc.queryForObject("select count(*) from book", Integer.class))
                .isEqualTo(17);
        assertThat(bookTitle("L2-B1")).isEqualTo("문장의 형식과 동사");
        assertThat(bookTitle("L1-B2")).isEqualTo("말의 종류");
        assertThat(bookTitle("L4-B2")).isEqualTo("준동사");
        assertThat(bookTitle("L1-B3")).isEqualTo("문장 넓히기");
        assertThat(bookTitle("L2-B3")).isEqualTo("기본 품사");
        assertThat(bookTitle("L3-B3")).isEqualTo("품사와 연결");
        assertThat(bookTitle("L1-B4")).isEqualTo("중1 준비");
        assertThat(bookTitle("L2-B4")).isEqualTo("접속사와 관계사");
        assertThat(bookTitle("L3-B4")).isEqualTo("절과 특수구문");
        assertThat(bookTitle("K-B1")).isEqualTo("고1 내신 실전");
        assertThat(jdbc.queryForObject("select season from book where code = 'L2-B3'", Integer.class))
                .isEqualTo(3);
        assertThat(jdbc.queryForObject("select season from book where code = 'K-B1'", Integer.class))
                .isEqualTo(1);
        // 블랙만 보충 4회를 더해 28(V5, ADR-27)
        assertThat(jdbc.queryForList("select code from book where lesson_count <> 24", String.class))
                .containsExactly("K-B1");
    }

    @Test
    void 회차는_412개고_코드가_규칙대로다() {
        assertThat(jdbc.queryForObject("select count(*) from lesson", Integer.class))
                .isEqualTo(412);
        assertThat(lesson("L2-B1-05"))
                .containsEntry("number", 5)
                .containsEntry("module", 1)
                .containsEntry("type", "regular")
                .containsEntry("title", "Reading Day 5")
                .containsEntry("passage_count", 6);
        assertThat(lesson("L2-B1-08")).containsEntry("type", "module_review");
        assertThat(lesson("L2-B1-09")).containsEntry("module", 2).containsEntry("type", "regular");
        assertThat(lesson("L4-B4-24"))
                .containsEntry("module", 3)
                .containsEntry("type", "module_review")
                .containsEntry("title", "Reading Day 24");
        // 블랙 말고는 권마다 1~24가 빠짐없이(블랙은 아래 테스트)
        assertThat(jdbc.queryForObject(
                        "select count(*) from (select book_id from lesson group by book_id"
                                + " having count(*) = 24 and min(number) = 1 and max(number) = 24) b",
                        Integer.class))
                .isEqualTo(16);
    }

    @Test
    void 블랙은_보충을_포함해_28회차를_진도표_순서로() {
        // ADR-27: 1~6 → 보충1 → 7~12 → 보충2 → 13~18 → 보충3 → 19~24 → 보충4. number = 권 안 진도표 순서
        assertThat(lesson("K-B1-06")).containsEntry("number", 6);
        assertThat(lesson("K-B1-S1"))
                .containsEntry("number", 7)
                .containsEntry("type", "supplement")
                .containsEntry("module", 1)
                .containsEntry("title", "보충 1")
                .containsEntry("passage_count", 6);
        assertThat(lesson("K-B1-07")).containsEntry("number", 8).containsEntry("type", "regular");
        assertThat(lesson("K-B1-08")).containsEntry("number", 9).containsEntry("module", 2);
        assertThat(lesson("K-B1-S2")).containsEntry("number", 14);
        assertThat(lesson("K-B1-13")).containsEntry("number", 15);
        assertThat(lesson("K-B1-S3")).containsEntry("number", 21);
        assertThat(lesson("K-B1-19")).containsEntry("number", 22);
        assertThat(lesson("K-B1-24")).containsEntry("number", 27).containsEntry("module", 4);
        assertThat(lesson("K-B1-S4")).containsEntry("number", 28).containsEntry("type", "supplement");
        // 블랙 본 회차는 모두 regular(V2가 8·16·24를 module_review로 넣었다)
        assertThat(jdbc.queryForObject(
                        "select count(*) from lesson l join book b on b.id = l.book_id"
                                + " where b.code = 'K-B1' and l.type = 'module_review'",
                        Integer.class))
                .isZero();
        assertThat(jdbc.queryForObject(
                        "select count(*) from lesson l join book b on b.id = l.book_id where b.code = 'K-B1'"
                                + " having min(l.number) = 1 and max(l.number) = 28 and count(distinct l.number) = 28",
                        Integer.class))
                .isEqualTo(28);
        assertThat(jdbc.queryForObject("select lesson_count from book where code = 'K-B1'", Integer.class))
                .isEqualTo(28);
        assertThat(jdbc.queryForObject("select lesson_count from book where code = 'L2-B1'", Integer.class))
                .isEqualTo(24);
    }

    @Test
    void 완료_규칙은_레벨마다_하나다() {
        assertThat(jdbc.queryForObject("select count(*) from completion_rule", Integer.class))
                .isEqualTo(5);
        assertThat(jdbc.queryForList("select distinct coverage_threshold from completion_rule", BigDecimal.class))
                .containsExactly(new BigDecimal("0.900"));
        for (String level : new String[] {"L1", "L2", "L3", "L4", "K"}) {
            assertThat(requiredVoicesEqual(level, "[\"us_w\",\"us_m\",\"uk_w\",\"uk_m\"]"))
                    .as(level)
                    .isTrue();
        }
        assertThat(jdbc.queryForObject(
                        "select count(*) from completion_rule where"
                                + " json_extract(required_tracks, '$[0].kind') = 'passage'"
                                + " and json_extract(required_tracks, '$[0].eachPassage') = true",
                        Integer.class))
                .isEqualTo(5);
    }

    private boolean requiredVoicesEqual(String level, String json) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "select json_extract(r.required_tracks, '$[0].anyOf') = cast(? as json)"
                        + " from completion_rule r join level l on l.id = r.level_id where l.code = ?",
                Boolean.class,
                json,
                level));
    }
}
