package com.jglearning.jg4seasons.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jglearning.jg4seasons.IntegrationTest;
import com.jglearning.jg4seasons.TestData;
import com.jglearning.jg4seasons.content.api.BookDirectory;
import com.jglearning.jg4seasons.content.api.BookLessons;
import com.jglearning.jg4seasons.content.api.BookSummary;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** 책 조회(V2·V5 기준 데이터): 입장 화면용 표시 정보, 반 생성용 회차(진도표 순서, 블랙은 보충 포함 28). */
@IntegrationTest
@Transactional
class BookDirectoryTest {

    @Autowired
    BookDirectory books;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void 책_요약은_시즌을_한국어로_준다() {
        long bookId = new TestData(jdbc).bookId("L2-B1");

        assertThat(books.summaryOf(bookId)).isEqualTo(new BookSummary("L2-B1", "겨울", "Book 1", "L2", "그린", "#1F7A4F"));
    }

    @Test
    void 시즌_네_개를_모두_옮긴다() {
        TestData data = new TestData(jdbc);

        assertThat(books.summaryOf(data.bookId("L2-B2")).season()).isEqualTo("봄");
        assertThat(books.summaryOf(data.bookId("L2-B3")).season()).isEqualTo("여름");
        assertThat(books.summaryOf(data.bookId("L2-B4")).season()).isEqualTo("가을");
    }

    @Test
    void 블랙_권의_시즌은_새봄() {
        // 블랙은 겨울 Book 1이 아니라 예비고1 "새봄 블랙"(12월에 겨울 세트와 함께 개강)
        TestData data = new TestData(jdbc);

        BookSummary black = books.summaryOf(data.bookId("K-B1"));

        assertThat(black.season()).isEqualTo("새봄");
        assertThat(black.label()).isEqualTo("Book 1");
    }

    @Test
    void 권_코드로_1회차부터_24회차_id를_찾는다() {
        long bookId = new TestData(jdbc).bookId("L2-B1");

        BookLessons lessons = books.findLessons("L2-B1").orElseThrow();

        assertThat(lessons.bookId()).isEqualTo(bookId);
        assertThat(lessons.lessonIdsByNumber()).hasSize(24).doesNotHaveDuplicates();
        assertThat(lessons.lessonIdsByNumber().get(0)).isEqualTo(lessonId("L2-B1-01"));
        assertThat(lessons.lessonIdsByNumber().get(4)).isEqualTo(lessonId("L2-B1-05"));
        assertThat(lessons.lessonIdsByNumber().get(23)).isEqualTo(lessonId("L2-B1-24"));
    }

    @Test
    void 블랙은_보충_회차를_포함해_진도표_순서로_28개() {
        BookLessons lessons = books.findLessons("K-B1").orElseThrow();

        assertThat(lessons.lessonIdsByNumber()).hasSize(28).doesNotHaveDuplicates();
        assertThat(lessons.lessonIdsByNumber().get(5)).isEqualTo(lessonId("K-B1-06"));
        assertThat(lessons.lessonIdsByNumber().get(6)).isEqualTo(lessonId("K-B1-S1"));
        assertThat(lessons.lessonIdsByNumber().get(7)).isEqualTo(lessonId("K-B1-07"));
        assertThat(lessons.lessonIdsByNumber().get(27)).isEqualTo(lessonId("K-B1-S4"));
    }

    @Test
    void 회차_수가_권의_회차_수와_다르면_IllegalState() {
        jdbc.update("update book set lesson_count = 25 where code = 'L2-B1'");

        assertThatThrownBy(() -> books.findLessons("L2-B1")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 없는_권_코드는_empty() {
        assertThat(books.findLessons("L9-B1")).isEmpty();
        assertThat(books.findLessons("")).isEmpty();
    }

    @Test
    void 공백은_정리하지_않고_대소문자는_DB_정렬_규칙대로_무관() {
        assertThat(books.findLessons(" L2-B1")).isEmpty();
        assertThat(books.findLessons("l2-b1").orElseThrow().bookId()).isEqualTo(new TestData(jdbc).bookId("L2-B1"));
    }

    @Test
    void 회차가_1부터_24가_아니면_데이터_오류() {
        long levelId = jdbc.queryForObject("select id from level where code = 'K'", Long.class);
        jdbc.update("insert into book (level_id, season, code, title) values (?, 2, 'TST-B1', '시험')", levelId);
        long bookId = jdbc.queryForObject("select id from book where code = 'TST-B1'", Long.class);
        for (int number = 1; number <= 3; number++) {
            jdbc.update(
                    "insert into lesson (book_id, code, number, module, type, title) values (?, ?, ?, 1, 'regular', ?)",
                    bookId,
                    "TST-B1-0" + number,
                    number,
                    "Reading Day " + number);
        }

        assertThatThrownBy(() -> books.findLessons("TST-B1")).isInstanceOf(IllegalStateException.class);
    }

    private long lessonId(String code) {
        return jdbc.queryForObject("select id from lesson where code = ?", Long.class, code);
    }

    @Test
    void 권_표시_이름은_Book_시즌_번호() {
        TestData data = new TestData(jdbc);

        assertThat(books.summaryOf(data.bookId("L2-B3")).label()).isEqualTo("Book 3");
        assertThat(books.summaryOf(data.bookId("L4-B2")).label()).isEqualTo("Book 2");
    }
}
