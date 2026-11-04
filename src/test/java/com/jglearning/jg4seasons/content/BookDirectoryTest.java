package com.jglearning.jg4seasons.content;

import static org.assertj.core.api.Assertions.assertThat;

import com.jglearning.jg4seasons.IntegrationTest;
import com.jglearning.jg4seasons.TestData;
import com.jglearning.jg4seasons.content.api.BookDirectory;
import com.jglearning.jg4seasons.content.api.BookSummary;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** 입장 화면용 책·레벨 표시 정보(V2 기준 데이터). */
@IntegrationTest
class BookDirectoryTest {

    @Autowired
    BookDirectory books;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void 책_요약은_시즌을_한국어로_준다() {
        long bookId = new TestData(jdbc).bookId("L2-B1");

        assertThat(books.summaryOf(bookId)).isEqualTo(new BookSummary("L2-B1", "겨울", "L2", "그린", "#1F7A4F"));
    }

    @Test
    void 시즌_네_개를_모두_옮긴다() {
        TestData data = new TestData(jdbc);

        assertThat(books.summaryOf(data.bookId("L2-B2")).season()).isEqualTo("봄");
        assertThat(books.summaryOf(data.bookId("L2-B3")).season()).isEqualTo("여름");
        assertThat(books.summaryOf(data.bookId("L2-B4")).season()).isEqualTo("가을");
    }
}
