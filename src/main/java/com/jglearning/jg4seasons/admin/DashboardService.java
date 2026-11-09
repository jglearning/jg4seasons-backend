package com.jglearning.jg4seasons.admin;

import com.jglearning.jg4seasons.academy.api.AcademyDirectory;
import com.jglearning.jg4seasons.admin.web.dto.DashboardResponse;
import com.jglearning.jg4seasons.admin.web.dto.DashboardResponse.DashboardClass;
import com.jglearning.jg4seasons.admin.web.dto.DashboardResponse.DashboardCurrent;
import com.jglearning.jg4seasons.admin.web.dto.DashboardResponse.DashboardLesson;
import com.jglearning.jg4seasons.auth.api.StaffAccounts;
import com.jglearning.jg4seasons.auth.api.StudentAccounts;
import com.jglearning.jg4seasons.clazz.api.ActiveClassToday;
import com.jglearning.jg4seasons.clazz.api.ClassLesson;
import com.jglearning.jg4seasons.clazz.api.ClassRoster;
import com.jglearning.jg4seasons.content.api.LessonCatalog;
import com.jglearning.jg4seasons.content.api.LessonSummary;
import com.jglearning.jg4seasons.listening.api.ProgressSummaries;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 5.4 학원 현황(A-04). 반·오늘 회차·소속은 clazz, 회차 코드·제목은 content, 완료·청취 수는 listening에서 받아 조립한다(다른 모듈 테이블 조인 없음). "오늘"은
 * 학원 타임존 날짜 — 오늘 들은 학생은 학원 자정부터 다음 자정 전까지의 last_played_at으로 센다(결정 F). 학원 범위는 clazz·auth api가 학원 id로 고른 학생
 * id로만 정해진다.
 */
@Service
@Transactional(readOnly = true)
public class DashboardService {

    private final AcademyDirectory academies;
    private final StaffAccounts staff;
    private final StudentAccounts students;
    private final ClassRoster roster;
    private final LessonCatalog catalog;
    private final ProgressSummaries progress;
    private final Clock clock;

    DashboardService(
            AcademyDirectory academies,
            StaffAccounts staff,
            StudentAccounts students,
            ClassRoster roster,
            LessonCatalog catalog,
            ProgressSummaries progress,
            Clock clock) {
        this.academies = academies;
        this.staff = staff;
        this.students = students;
        this.roster = roster;
        this.catalog = catalog;
        this.progress = progress;
        this.clock = clock;
    }

    public DashboardResponse dashboard(long academyId, long actorId) {
        staff.requireActiveAdmin(academyId, actorId);
        ZoneId zone = academies.zoneOf(academyId);
        LocalDate today = LocalDate.now(clock.withZone(zone));
        Map<Long, Map<Long, LessonSummary>> lessonsByBook = new HashMap<>();
        List<DashboardClass> classes = roster.activeClassesToday(academyId, today).stream()
                .map(c -> toClass(c, lessonsByBook))
                .toList();
        int activeStudentsToday = progress.playedBetween(
                students.studentIds(academyId),
                today.atStartOfDay(zone).toInstant(),
                today.plusDays(1).atStartOfDay(zone).toInstant());
        return new DashboardResponse(classes, activeStudentsToday);
    }

    private DashboardClass toClass(ActiveClassToday c, Map<Long, Map<Long, LessonSummary>> lessonsByBook) {
        if (c.today().isEmpty()) {
            return new DashboardClass(c.classId(), c.name(), null, 0);
        }
        ClassLesson today = c.today().get();
        LessonSummary lesson =
                lessonsByBook.computeIfAbsent(c.bookId(), this::lessonsOf).get(today.lessonId());
        if (lesson == null) {
            throw new IllegalStateException(
                    "반 진도표 회차가 그 반 권에 없다: class " + c.classId() + " lesson " + today.lessonId());
        }
        double completedRate = c.studentIds().isEmpty()
                ? 0
                : (double) progress.completedCount(today.lessonId(), c.studentIds())
                        / c.studentIds().size();
        return new DashboardClass(
                c.classId(),
                c.name(),
                new DashboardCurrent(
                        today.seq(), new DashboardLesson(lesson.code(), lesson.title()), today.scheduledDate()),
                completedRate);
    }

    private Map<Long, LessonSummary> lessonsOf(long bookId) {
        return catalog.lessonsOfBook(bookId).stream()
                .collect(Collectors.toMap(LessonSummary::lessonId, Function.identity()));
    }
}
