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

    /** 블랙(레벨 K)은 계절 세트가 아니라 예비고1 "새봄 블랙" 한 권이다(ADR 0027 결정 5). 회차 묶음 이름도 "새봄 1"처럼 */
    private static final String BLACK_LEVEL = "K";

    private static final String BLACK_SEASON = "새봄";

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
                    IntStream.rangeClosed(1, book.getLessonCount()).boxed().toList();
            if (!numbers.equals(expected)) {
                throw new IllegalStateException("권의 회차가 1~" + book.getLessonCount() + "가 아니다: " + book.getCode());
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
                BLACK_LEVEL.equals(level.getCode()) ? BLACK_SEASON : SEASONS.get(book.getSeason() - 1),
                "Book " + book.getSeason(),
                level.getCode(),
                level.getNameKo(),
                level.getColorHex());
    }
}
