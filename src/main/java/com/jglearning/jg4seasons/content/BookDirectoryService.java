package com.jglearning.jg4seasons.content;

import com.jglearning.jg4seasons.content.api.BookDirectory;
import com.jglearning.jg4seasons.content.api.BookLessons;
import com.jglearning.jg4seasons.content.api.BookSummary;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
class BookDirectoryService implements BookDirectory {

    /** book.season 1~4 */
    private static final List<String> SEASONS = List.of("겨울", "봄", "여름", "가을");

    private final BookRepository books;
    private final LessonRepository lessons;

    BookDirectoryService(BookRepository books, LessonRepository lessons) {
        this.books = books;
        this.lessons = lessons;
    }

    @Override
    public Optional<BookLessons> findLessons(String bookCode) {
        return books.findByCode(bookCode).map(book -> {
            List<Lesson> found = lessons.findByBookIdOrderByNumber(book.getId());
            List<Integer> numbers = found.stream().map(Lesson::getNumber).toList();
            List<Integer> expected =
                    IntStream.rangeClosed(1, LESSONS_PER_BOOK).boxed().toList();
            if (!numbers.equals(expected)) {
                throw new IllegalStateException("권의 회차가 1~" + LESSONS_PER_BOOK + "가 아니다: " + book.getCode());
            }
            return new BookLessons(
                    book.getId(), found.stream().map(Lesson::getId).toList());
        });
    }

    @Override
    public BookSummary summaryOf(long bookId) {
        Book book = books.findById(bookId).orElseThrow(() -> new IllegalStateException("책이 없다: " + bookId));
        Level level = book.getLevel();
        return new BookSummary(
                book.getCode(),
                SEASONS.get(book.getSeason() - 1),
                "Book " + book.getSeason(),
                level.getCode(),
                level.getNameKo(),
                level.getColorHex());
    }
}
