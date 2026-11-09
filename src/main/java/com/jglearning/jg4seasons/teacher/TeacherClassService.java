package com.jglearning.jg4seasons.teacher;

import com.jglearning.jg4seasons.academy.api.AcademyDirectory;
import com.jglearning.jg4seasons.auth.api.LoginAlerts;
import com.jglearning.jg4seasons.auth.api.StaffAccounts;
import com.jglearning.jg4seasons.auth.api.StaffRole;
import com.jglearning.jg4seasons.clazz.api.ClassLesson;
import com.jglearning.jg4seasons.clazz.api.TeacherClass;
import com.jglearning.jg4seasons.clazz.api.TeacherClasses;
import com.jglearning.jg4seasons.content.api.BookDirectory;
import com.jglearning.jg4seasons.content.api.BookSummary;
import com.jglearning.jg4seasons.content.api.LessonCatalog;
import com.jglearning.jg4seasons.content.api.LessonSummary;
import com.jglearning.jg4seasons.listening.api.LessonBoard;
import com.jglearning.jg4seasons.listening.api.LessonBoard.Board;
import com.jglearning.jg4seasons.listening.api.LessonProgressQuery;
import com.jglearning.jg4seasons.listening.api.LessonProgressStatus;
import com.jglearning.jg4seasons.teacher.web.dto.TeacherClassBook;
import com.jglearning.jg4seasons.teacher.web.dto.TeacherClassLevel;
import com.jglearning.jg4seasons.teacher.web.dto.TeacherClassListResponse;
import com.jglearning.jg4seasons.teacher.web.dto.TeacherClassListResponse.LoginAlert;
import com.jglearning.jg4seasons.teacher.web.dto.TeacherClassListResponse.TeacherClassCurrent;
import com.jglearning.jg4seasons.teacher.web.dto.TeacherClassListResponse.TeacherClassItem;
import com.jglearning.jg4seasons.teacher.web.dto.TeacherClassListResponse.TeacherClassNext;
import com.jglearning.jg4seasons.teacher.web.dto.TeacherLessonRef;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
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

    private final AcademyDirectory academies;
    private final StaffAccounts staff;
    private final TeacherClasses teacherClasses;
    private final LessonProgressQuery progress;
    private final BookDirectory books;
    private final LessonCatalog catalog;
    private final LoginAlerts loginAlerts;
    private final Clock clock;

    TeacherClassService(
            AcademyDirectory academies,
            StaffAccounts staff,
            TeacherClasses teacherClasses,
            LessonProgressQuery progress,
            BookDirectory books,
            LessonCatalog catalog,
            LoginAlerts loginAlerts,
            Clock clock) {
        this.academies = academies;
        this.staff = staff;
        this.teacherClasses = teacherClasses;
        this.progress = progress;
        this.books = books;
        this.catalog = catalog;
        this.loginAlerts = loginAlerts;
        this.clock = clock;
    }

    public TeacherClassListResponse classes(long academyId, long actorId) {
        boolean wholeAcademy = staff.requireActiveStaff(academyId, actorId) == StaffRole.ADMIN;
        LocalDate today = today(academyId);
        List<TeacherClassItem> items = teacherClasses.activeClasses(academyId, actorId, wholeAcademy, today).stream()
                .map(clazz -> toItem(clazz, today))
                .toList();
        return new TeacherClassListResponse(items);
    }

    private TeacherClassItem toItem(TeacherClass clazz, LocalDate today) {
        Map<Long, Map<Long, LessonProgressStatus>> statuses = statusesOf(clazz);
        Board classBoard = LessonBoard.evaluate(clazz.lessons(), Map.of(), today); // 오늘 회차·다음 수업은 학생과 무관
        int overdueStudents = (int) clazz.studentIds().stream()
                .filter(id -> !LessonBoard.evaluate(clazz.lessons(), statuses.getOrDefault(id, Map.of()), today)
                        .overdue()
                        .isEmpty())
                .count();
        BookSummary book = books.summaryOf(clazz.bookId());
        TeacherClassCurrent current = classBoard
                .today()
                .map(lesson -> current(clazz, lesson, statuses))
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
                clazz.studentIds().size(),
                current,
                overdueStudents,
                loginAlert,
                next);
    }

    private TeacherClassCurrent current(
            TeacherClass clazz, ClassLesson lesson, Map<Long, Map<Long, LessonProgressStatus>> statuses) {
        int completed = 0;
        int inProgress = 0;
        for (long studentId : clazz.studentIds()) {
            LessonProgressStatus status =
                    statuses.getOrDefault(studentId, Map.of()).get(lesson.lessonId());
            if (status == LessonProgressStatus.COMPLETED) {
                completed++;
            } else if (status == LessonProgressStatus.IN_PROGRESS) {
                inProgress++;
            }
        }
        LessonSummary summary = lessonsOf(clazz).get(lesson.lessonId());
        if (summary == null) {
            throw new IllegalStateException(
                    "반 진도표 회차가 그 반 권에 없다: class " + clazz.classId() + " lesson " + lesson.lessonId());
        }
        return new TeacherClassCurrent(
                lesson.seq(),
                new TeacherLessonRef(summary.code(), summary.title()),
                lesson.scheduledDate(),
                completed,
                inProgress,
                clazz.studentIds().size() - completed - inProgress);
    }

    /** 학생 id → 회차 id → 상태(진도 행이 있는 것만) */
    private Map<Long, Map<Long, LessonProgressStatus>> statusesOf(TeacherClass clazz) {
        List<Long> lessonIds =
                clazz.lessons().stream().map(ClassLesson::lessonId).toList();
        return progress.progressOf(clazz.studentIds(), lessonIds).entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> e.getValue().entrySet().stream()
                                .collect(Collectors.toMap(
                                        Map.Entry::getKey, p -> p.getValue().status()))));
    }

    private Map<Long, LessonSummary> lessonsOf(TeacherClass clazz) {
        return catalog.lessonsOfBook(clazz.bookId()).stream()
                .collect(Collectors.toMap(LessonSummary::lessonId, Function.identity()));
    }

    private LocalDate today(long academyId) {
        return LocalDate.now(clock.withZone(academies.zoneOf(academyId)));
    }
}
