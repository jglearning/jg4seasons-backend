package com.jglearning.jg4seasons.listening;

import com.jglearning.jg4seasons.academy.api.AcademyDirectory;
import com.jglearning.jg4seasons.clazz.api.ClassLesson;
import com.jglearning.jg4seasons.clazz.api.StudentClass;
import com.jglearning.jg4seasons.clazz.api.StudentClasses;
import com.jglearning.jg4seasons.content.api.BookDirectory;
import com.jglearning.jg4seasons.content.api.BookSummary;
import com.jglearning.jg4seasons.content.api.LessonCatalog;
import com.jglearning.jg4seasons.content.api.LessonContent;
import com.jglearning.jg4seasons.content.api.LessonSummary;
import com.jglearning.jg4seasons.infra.web.error.ApiException;
import com.jglearning.jg4seasons.infra.web.error.CommonErrorCode;
import com.jglearning.jg4seasons.listening.LessonBoard.Board;
import com.jglearning.jg4seasons.listening.api.LessonProgressStatus;
import com.jglearning.jg4seasons.listening.persistence.ProgressStore;
import com.jglearning.jg4seasons.listening.persistence.ProgressStore.LessonProgressRow;
import com.jglearning.jg4seasons.listening.web.dto.LessonProgressView;
import com.jglearning.jg4seasons.listening.web.dto.StudentClassInfo;
import com.jglearning.jg4seasons.listening.web.dto.StudentHomeResponse;
import com.jglearning.jg4seasons.words.api.WordStudies;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 2.1 학생 홈. 학생·학원은 JWT에서만. 반 진도표(clazz) + 권의 회차(content) + 진도(listening 집계)를 모아 LessonBoard로 상태를 정한다. 오늘 = 학원 타임존 날짜.
 * 진도 집계는 학생이 그 학원 반 소속임을 확인한 뒤에만 읽는다(academy_id 없는 테이블).
 */
@Service
@Transactional(readOnly = true)
public class StudentHomeService {

    private static final Logger log = LoggerFactory.getLogger(StudentHomeService.class);

    private final AcademyDirectory academies;
    private final StudentClasses studentClasses;
    private final BookDirectory books;
    private final LessonCatalog catalog;
    private final ProgressStore progress;
    private final WordStudies wordStudies;
    private final Clock clock;

    StudentHomeService(
            AcademyDirectory academies,
            StudentClasses studentClasses,
            BookDirectory books,
            LessonCatalog catalog,
            ProgressStore progress,
            WordStudies wordStudies,
            Clock clock) {
        this.academies = academies;
        this.studentClasses = studentClasses;
        this.books = books;
        this.catalog = catalog;
        this.progress = progress;
        this.wordStudies = wordStudies;
        this.clock = clock;
    }

    /** 한 반의 회차판: 반·권·권의 회차·진도·판정 결과 */
    record ClassBoard(
            StudentClass clazz,
            BookSummary book,
            Map<Long, LessonSummary> lessons,
            Map<Long, LessonProgressRow> progress,
            Board board) {}

    public StudentHomeResponse home(long academyId, long studentId) {
        List<StudentClass> classes = currentClasses(academyId, studentId);
        ClassBoard board = board(studentId, classes.get(0), academyId);
        long studentClassId = board.clazz().classId();

        StudentHomeResponse.Today today = null;
        StudentHomeResponse.Words words = null;
        if (board.board().today().isPresent()) {
            ClassLesson lesson = board.board().today().get();
            LessonSummary summary = board.lessons().get(lesson.lessonId());
            LessonContent content = catalog.findByCode(summary.code()) // 오늘 회차만 내용을 읽는다(지문 제목·단어 수)
                    .orElseThrow(() -> new IllegalStateException("회차가 없다: " + summary.code()));
            today = today(board, lesson, summary, content);
            words = new StudentHomeResponse.Words(
                    summary.code(), content.wordTotal(), wordStudies.memKnown(studentId, summary.lessonId()));
        }
        StudentHomeResponse.Next next = board.board()
                .next()
                .map(lesson -> new StudentHomeResponse.Next(
                        lesson.seq(),
                        lesson.scheduledDate(),
                        ref(board.lessons().get(lesson.lessonId()))))
                .orElse(null);
        List<StudentHomeResponse.Overdue> overdue = board.board().overdue().stream()
                .map(entry -> new StudentHomeResponse.Overdue(
                        entry.lesson().seq(),
                        entry.lesson().scheduledDate(),
                        ref(board.lessons().get(entry.lesson().lessonId())),
                        entry.overdueKind().value(),
                        progressView(board, entry.lesson().lessonId())))
                .toList();
        return new StudentHomeResponse(
                classInfo(board),
                today,
                overdue,
                words,
                next,
                new StudentHomeResponse.Stats(
                        board.board().completedCount(),
                        overdue.size(),
                        board.board().entries().size()),
                classes.stream()
                        .filter(other -> other.classId() != studentClassId)
                        .map(other -> new StudentHomeResponse.OtherClass(other.classId(), other.name()))
                        .toList());
    }

    /** 학원 활성 확인 뒤 현재 소속 active 반(id 순). 없으면 404 */
    List<StudentClass> currentClasses(long academyId, long studentId) {
        if (!academies.isActive(academyId)) {
            throw new ApiException(CommonErrorCode.UNAUTHORIZED);
        }
        List<StudentClass> classes = studentClasses.currentClasses(academyId, studentId);
        if (classes.isEmpty()) {
            throw new ApiException(CommonErrorCode.NOT_FOUND, "반을 찾을 수 없어요");
        }
        return classes;
    }

    ClassBoard board(long studentId, StudentClass clazz, long academyId) {
        LocalDate today = LocalDate.now(clock.withZone(academies.zoneOf(academyId)));
        List<ClassLesson> classLessons = studentClasses.lessons(clazz, today);
        Map<Long, LessonSummary> lessons = catalog.lessonsOfBook(clazz.bookId()).stream()
                .collect(Collectors.toMap(LessonSummary::lessonId, Function.identity()));
        Map<Long, LessonProgressRow> rows = progress.lessonProgress(
                studentId, classLessons.stream().map(ClassLesson::lessonId).toList());
        Map<Long, LessonProgressStatus> statuses = rows.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey, row -> row.getValue().status()));
        for (ClassLesson lesson : classLessons) {
            if (!lessons.containsKey(lesson.lessonId())) {
                throw new IllegalStateException("진도표 회차가 반의 권에 없다: class " + clazz.classId());
            }
        }
        return new ClassBoard(
                clazz,
                books.summaryOf(clazz.bookId()),
                lessons,
                rows,
                LessonBoard.evaluate(classLessons, statuses, today));
    }

    StudentClassInfo classInfo(ClassBoard board) {
        BookSummary book = board.book();
        return new StudentClassInfo(
                board.clazz().classId(),
                board.clazz().name(),
                new StudentClassInfo.Level(book.levelCode(), book.levelNameKo(), book.levelColorHex()),
                new StudentClassInfo.Book(book.bookCode(), book.season(), book.label()));
    }

    /** 기록이 없으면 not_started·0·지문 수. 집계의 지문 수가 실제와 다르면 실제 수(2.3과 같음) */
    LessonProgressView progressView(ClassBoard board, long lessonId) {
        int total = board.lessons().get(lessonId).passageCount();
        LessonProgressRow row = board.progress().get(lessonId);
        if (row == null) {
            return new LessonProgressView(LessonProgressStatus.NOT_STARTED.value(), 0, total);
        }
        if (row.passagesTotal() != total) {
            log.warn("회차 집계의 지문 수가 실제와 다르다: lesson {} 집계 {} 실제 {}", lessonId, row.passagesTotal(), total);
        }
        return new LessonProgressView(row.status().value(), Math.min(row.passagesDone(), total), total);
    }

    static String groupLabel(BookSummary book, LessonSummary lesson) {
        return book.season() + " " + lesson.module();
    }

    private StudentHomeResponse.Today today(
            ClassBoard board, ClassLesson lesson, LessonSummary summary, LessonContent content) {
        return new StudentHomeResponse.Today(
                lesson.seq(),
                lesson.scheduledDate(),
                groupLabel(board.book(), summary),
                new StudentHomeResponse.TodayLesson(
                        summary.code(),
                        summary.number(),
                        summary.title(),
                        summary.type(),
                        content.passages().stream()
                                .map(passage -> new StudentHomeResponse.PassageTitle(passage.no(), passage.title()))
                                .toList()),
                progressView(board, lesson.lessonId()));
    }

    private static StudentHomeResponse.LessonRef ref(LessonSummary lesson) {
        return new StudentHomeResponse.LessonRef(lesson.code(), lesson.title());
    }
}
