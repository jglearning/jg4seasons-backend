package com.jglearning.jg4seasons.teacher;

import com.jglearning.jg4seasons.auth.api.LoginAlerts;
import com.jglearning.jg4seasons.auth.api.StudentAccount;
import com.jglearning.jg4seasons.clazz.api.ClassLesson;
import com.jglearning.jg4seasons.clazz.api.SessionStatus;
import com.jglearning.jg4seasons.clazz.api.TeacherClass;
import com.jglearning.jg4seasons.content.api.BookDirectory;
import com.jglearning.jg4seasons.content.api.BookSummary;
import com.jglearning.jg4seasons.content.api.LessonSummary;
import com.jglearning.jg4seasons.listening.api.LessonBoard;
import com.jglearning.jg4seasons.listening.api.LessonBoard.Board;
import com.jglearning.jg4seasons.listening.api.LessonProgressQuery;
import com.jglearning.jg4seasons.listening.api.LessonProgressStatus;
import com.jglearning.jg4seasons.listening.api.LessonProgressSummary;
import com.jglearning.jg4seasons.teacher.web.dto.MatrixResponse;
import com.jglearning.jg4seasons.teacher.web.dto.MatrixResponse.MatrixCell;
import com.jglearning.jg4seasons.teacher.web.dto.MatrixResponse.MatrixClass;
import com.jglearning.jg4seasons.teacher.web.dto.MatrixResponse.MatrixLesson;
import com.jglearning.jg4seasons.teacher.web.dto.MatrixResponse.MatrixSession;
import com.jglearning.jg4seasons.teacher.web.dto.MatrixResponse.MatrixStudent;
import com.jglearning.jg4seasons.teacher.web.dto.TeacherClassBook;
import com.jglearning.jg4seasons.teacher.web.dto.TeacherClassLevel;
import com.jglearning.jg4seasons.teacher.web.dto.TeacherClassListResponse;
import com.jglearning.jg4seasons.teacher.web.dto.TeacherClassListResponse.LoginAlert;
import com.jglearning.jg4seasons.teacher.web.dto.TeacherClassListResponse.TeacherClassCurrent;
import com.jglearning.jg4seasons.teacher.web.dto.TeacherClassListResponse.TeacherClassItem;
import com.jglearning.jg4seasons.teacher.web.dto.TeacherClassListResponse.TeacherClassNext;
import com.jglearning.jg4seasons.teacher.web.dto.TeacherLessonRef;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 4.1 담당 반 목록·4.2 청취 현황 매트릭스(TCH-01·02). 반·진도표·소속은 clazz, 진도는 listening, 회차·권 표시는 content, 활성 교사·로그인 경고는
 * auth에서 받아 조립한다(다른 모듈 테이블 조인 없음). 범위는 DB의 지금 역할로 나눈다 — 관리자는 학원 전체, 교사는 담당 반. 오늘 = 학원 타임존 날짜, 오늘 회차·
 * 밀림·다음 수업은 학생 화면과 같은 LessonBoard 규칙.
 */
@Service
@Transactional(readOnly = true)
public class TeacherClassService {

    /** 범위를 빼면 오늘 회차가 든 8개 묶음(결정 C) */
    static final int SEQ_BLOCK = 8;

    private final TeacherScope scope;
    private final LessonProgressQuery progress;
    private final BookDirectory books;
    private final LoginAlerts loginAlerts;

    TeacherClassService(
            TeacherScope scope, LessonProgressQuery progress, BookDirectory books, LoginAlerts loginAlerts) {
        this.scope = scope;
        this.progress = progress;
        this.books = books;
        this.loginAlerts = loginAlerts;
    }

    public TeacherClassListResponse classes(long academyId, long actorId) {
        List<TeacherClassItem> items = scope.activeClasses(academyId, actorId).stream()
                .map(this::toItem)
                .toList();
        return new TeacherClassListResponse(items);
    }

    /**
     * 4.2. 범위(seq, 포함)는 둘 다 오거나 둘 다 null(컨트롤러가 검증). 열 = 접은 회차 중 휴강 행만 있는 것은 빼고 범위 안, seq 순. 칸 = 열린 회차마다 현재 학생
     * 전부(진도 없으면 not_started·0/지문 수 — 결정 B, 저장된 지문 수가 지금과 다르면 지금 지문 수 — TeacherScope.progressOf)
     */
    public MatrixResponse matrix(long academyId, long actorId, long classId, Integer fromSeq, Integer toSeq) {
        TeacherScope.ScopedClass scoped = scope.classOf(academyId, actorId, classId);
        LocalDate today = scoped.today();
        TeacherClass clazz = scoped.clazz();
        Board classBoard = LessonBoard.evaluate(clazz.lessons(), Map.of(), today);
        int from;
        int to;
        if (fromSeq != null) {
            from = fromSeq;
            to = toSeq;
        } else {
            int todaySeq = classBoard.today().map(ClassLesson::seq).orElse(1);
            from = (todaySeq - 1) / SEQ_BLOCK * SEQ_BLOCK + 1;
            to = from + SEQ_BLOCK - 1;
        }
        List<ClassLesson> columns = clazz.lessons().stream()
                .filter(ClassLesson::held)
                .filter(lesson -> lesson.seq() >= from && lesson.seq() <= to)
                .toList();
        Map<Long, LessonSummary> lessons = scope.lessonsOf(scoped);
        List<StudentAccount> rows = scope.roster(scoped).accounts();
        Map<Long, Map<Long, LessonProgressSummary>> progressById = progress.progressOf(
                rows.stream().map(StudentAccount::id).toList(),
                clazz.lessons().stream().map(ClassLesson::lessonId).toList());
        long todayLessonId = classBoard.today().map(ClassLesson::lessonId).orElse(-1L);

        List<MatrixSession> sessions = columns.stream()
                .map(lesson -> {
                    LessonSummary summary = TeacherScope.summaryOf(lessons, clazz, lesson);
                    return new MatrixSession(
                            lesson.seq(),
                            new MatrixLesson(summary.code(), summary.number(), summary.title()),
                            lesson.scheduledDate(),
                            clazz.doneLessonIds().contains(lesson.lessonId())
                                    ? SessionStatus.DONE.value()
                                    : SessionStatus.SCHEDULED.value(),
                            lesson.lessonId() == todayLessonId,
                            lesson.open());
                })
                .toList();
        Map<String, Map<String, MatrixCell>> cells = new LinkedHashMap<>();
        for (StudentAccount student : rows) {
            Map<Long, LessonProgressSummary> mine = progressById.getOrDefault(student.id(), Map.of());
            Map<Long, LessonProgressStatus> statuses = mine.entrySet().stream()
                    .collect(Collectors.toMap(
                            Map.Entry::getKey, e -> e.getValue().status()));
            Set<Long> overdue = LessonBoard.evaluate(clazz.lessons(), statuses, today).overdue().stream()
                    .map(entry -> entry.lesson().lessonId())
                    .collect(Collectors.toSet());
            Map<String, MatrixCell> row = new LinkedHashMap<>();
            for (ClassLesson lesson : columns) {
                if (!lesson.open()) {
                    continue;
                }
                LessonSummary summary = TeacherScope.summaryOf(lessons, clazz, lesson);
                LessonProgressSummary p = TeacherScope.progressOf(mine.get(lesson.lessonId()), summary, student.id());
                row.put(
                        summary.code(),
                        new MatrixCell(
                                p.status().value(),
                                p.requiredDone(),
                                p.requiredTotal(),
                                p.completedAt(),
                                overdue.contains(lesson.lessonId())));
            }
            cells.put(String.valueOf(student.id()), row);
        }
        BookSummary book = books.summaryOf(clazz.bookId());
        return new MatrixResponse(
                new MatrixClass(
                        clazz.classId(),
                        clazz.name(),
                        new TeacherClassLevel(book.levelCode(), book.levelNameKo(), book.levelColorHex())),
                sessions,
                rows.stream()
                        .map(s -> new MatrixStudent(s.id(), s.name(), s.school(), s.grade()))
                        .toList(),
                cells);
    }

    private TeacherClassItem toItem(TeacherScope.ScopedClass scoped) {
        TeacherClass clazz = scoped.clazz();
        LocalDate today = scoped.today();
        List<Long> studentIds =
                scope.roster(scoped).accounts().stream().map(StudentAccount::id).toList();
        Map<Long, Map<Long, LessonProgressStatus>> statuses = statusesOf(clazz, studentIds);
        Board classBoard = LessonBoard.evaluate(clazz.lessons(), Map.of(), today); // 오늘 회차·다음 수업은 학생과 무관
        int overdueStudents = (int) studentIds.stream()
                .filter(id -> !LessonBoard.evaluate(clazz.lessons(), statuses.getOrDefault(id, Map.of()), today)
                        .overdue()
                        .isEmpty())
                .count();
        BookSummary book = books.summaryOf(clazz.bookId());
        TeacherClassCurrent current = classBoard
                .today()
                .map(lesson -> current(scoped, lesson, statuses, studentIds))
                .orElse(null);
        TeacherClassNext next = classBoard
                .next()
                .map(lesson -> new TeacherClassNext(lesson.seq(), lesson.scheduledDate()))
                .orElse(null);
        LoginAlert loginAlert = loginAlerts
                .alertOf(clazz.classId())
                .map(alert -> new LoginAlert(alert.failures(), alert.since()))
                .orElse(null);
        return new TeacherClassItem(
                clazz.classId(),
                clazz.name(),
                new TeacherClassLevel(book.levelCode(), book.levelNameKo(), book.levelColorHex()),
                new TeacherClassBook(book.bookCode(), book.label(), book.season()),
                studentIds.size(),
                current,
                overdueStudents,
                loginAlert,
                next);
    }

    private TeacherClassCurrent current(
            TeacherScope.ScopedClass scoped,
            ClassLesson lesson,
            Map<Long, Map<Long, LessonProgressStatus>> statuses,
            List<Long> studentIds) {
        int completed = 0;
        int inProgress = 0;
        for (long studentId : studentIds) {
            LessonProgressStatus status =
                    statuses.getOrDefault(studentId, Map.of()).get(lesson.lessonId());
            if (status == LessonProgressStatus.COMPLETED) {
                completed++;
            } else if (status == LessonProgressStatus.IN_PROGRESS) {
                inProgress++;
            }
        }
        LessonSummary summary = TeacherScope.summaryOf(scope.lessonsOf(scoped), scoped.clazz(), lesson);
        return new TeacherClassCurrent(
                lesson.seq(),
                new TeacherLessonRef(summary.code(), summary.title()),
                lesson.scheduledDate(),
                completed,
                inProgress,
                studentIds.size() - completed - inProgress);
    }

    /** 학생 id → 회차 id → 상태(진도 행이 있는 것만) */
    private Map<Long, Map<Long, LessonProgressStatus>> statusesOf(TeacherClass clazz, List<Long> studentIds) {
        List<Long> lessonIds =
                clazz.lessons().stream().map(ClassLesson::lessonId).toList();
        return progress.progressOf(studentIds, lessonIds).entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> e.getValue().entrySet().stream()
                                .collect(Collectors.toMap(
                                        Map.Entry::getKey, p -> p.getValue().status()))));
    }
}
