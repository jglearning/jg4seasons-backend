package com.jglearning.jg4seasons.content;

import com.jglearning.jg4seasons.content.api.BookDirectory;
import com.jglearning.jg4seasons.content.api.BookSummary;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
class BookDirectoryService implements BookDirectory {

    /** book.season 1~4 */
    private static final List<String> SEASONS = List.of("겨울", "봄", "여름", "가을");

    private final BookRepository books;

    BookDirectoryService(BookRepository books) {
        this.books = books;
    }

    @Override
    public BookSummary summaryOf(long bookId) {
        Book book = books.findById(bookId).orElseThrow(() -> new IllegalStateException("책이 없다: " + bookId));
        Level level = book.getLevel();
        return new BookSummary(
                book.getCode(),
                SEASONS.get(book.getSeason() - 1),
                level.getCode(),
                level.getNameKo(),
                level.getColorHex());
    }
}
