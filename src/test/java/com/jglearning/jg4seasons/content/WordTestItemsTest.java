package com.jglearning.jg4seasons.content;

import static org.assertj.core.api.Assertions.assertThat;

import com.jglearning.jg4seasons.IntegrationTest;
import com.jglearning.jg4seasons.TestData;
import com.jglearning.jg4seasons.content.api.WordTestItem;
import com.jglearning.jg4seasons.content.api.WordTestItems;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** 시험지 문항 읽기(공용 콘텐츠): 회차·영역별, 번호 순, 허용 답 포함. 문항은 지어낸 것 */
@IntegrationTest
@Transactional
class WordTestItemsTest {

    @Autowired
    WordTestItems items;

    @Autowired
    JdbcTemplate jdbc;

    long lesson;
    long other;

    @BeforeEach
    void setUp() {
        TestData data = new TestData(jdbc);
        lesson = data.lessonId("L1-B1-01");
        other = data.lessonId("L1-B1-02");
        long second = item(lesson, "B", 2, "웅덩이", "puddle", null);
        item(lesson, "B", 1, "분위기", "atmosphere", "a");
        item(lesson, "A", 1, "kite", "연", null);
        item(other, "B", 1, "연", "kite", null);
        accepted(second, "puddel");
        accepted(second, "puddle s");
    }

    @Test
    void 영역의_문항을_번호_순으로_허용_답과_함께_읽는다() {
        List<WordTestItem> found = items.of(lesson, "B");

        assertThat(found)
                .extracting(WordTestItem::no, WordTestItem::prompt, WordTestItem::answer, WordTestItem::hint)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(1, "분위기", "atmosphere", "a"),
                        org.assertj.core.groups.Tuple.tuple(2, "웅덩이", "puddle", null));
        assertThat(found.get(0).accepted()).isEmpty();
        assertThat(found.get(1).accepted()).containsExactly("puddel", "puddle s");
    }

    @Test
    void 개수는_그_회차_그_영역만_센다() {
        assertThat(items.count(lesson, "B")).isEqualTo(2);
        assertThat(items.count(lesson, "A")).isEqualTo(1);
        assertThat(items.count(lesson, "C")).isZero();
        assertThat(items.of(lesson, "C")).isEmpty();
        assertThat(items.count(other, "B")).isEqualTo(1);
    }

    private long item(long lessonId, String section, int no, String prompt, String answer, String hint) {
        jdbc.update(
                "insert into word_test_item (lesson_id, section, no, prompt, answer, hint) values (?, ?, ?, ?, ?, ?)",
                lessonId,
                section,
                no,
                prompt,
                answer,
                hint);
        return jdbc.queryForObject("select last_insert_id()", Long.class);
    }

    private void accepted(long itemId, String text) {
        jdbc.update("insert into word_test_accepted_answer (item_id, text) values (?, ?)", itemId, text);
    }
}
