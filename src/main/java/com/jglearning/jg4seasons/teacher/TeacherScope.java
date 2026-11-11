package com.jglearning.jg4seasons.teacher;

import com.jglearning.jg4seasons.academy.api.AcademyDirectory;
import com.jglearning.jg4seasons.auth.api.StaffAccounts;
import com.jglearning.jg4seasons.auth.api.StaffRole;
import com.jglearning.jg4seasons.auth.api.StudentAccount;
import com.jglearning.jg4seasons.auth.api.StudentAccounts;
import com.jglearning.jg4seasons.auth.api.StudentRoster;
import com.jglearning.jg4seasons.clazz.api.ClassLesson;
import com.jglearning.jg4seasons.clazz.api.ClassSchedules;
import com.jglearning.jg4seasons.clazz.api.ScheduleChange;
import com.jglearning.jg4seasons.clazz.api.ScheduleEntry;
import com.jglearning.jg4seasons.clazz.api.TeacherClass;
import com.jglearning.jg4seasons.clazz.api.TeacherClasses;
import com.jglearning.jg4seasons.content.api.LessonCatalog;
import com.jglearning.jg4seasons.content.api.LessonSummary;
import com.jglearning.jg4seasons.listening.api.LessonProgressStatus;
import com.jglearning.jg4seasons.listening.api.LessonProgressSummary;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 교사 API(4.1~4.4)가 같이 쓰는 범위·학생 규칙. 범위는 DB의 지금 역할로 나눈다 — 관리자는 학원 전체, 교사는 담당 반(JWT 역할 아님). 학생 목록은 반의 현재
 * 소속 중 그 학원 학생만 — 화면마다 같은 목록을 써야 숫자가 맞는다.
 */
@Component
class TeacherScope {

    private static final Logger log = LoggerFactory.getLogger(TeacherScope.class);

    private final AcademyDirectory academies;
    private final StaffAccounts staff;
    private final StudentAccounts students;
    private final TeacherClasses teacherClasses;
    private final LessonCatalog catalog;
    private final ClassSchedules schedules;
    private final Clock clock;

    TeacherScope(
            AcademyDirectory academies,
            StaffAccounts staff,
            StudentAccounts students,
            TeacherClasses teacherClasses,
            LessonCatalog catalog,
            ClassSchedules schedules,
            Clock clock) {
        this.academies = academies;
        this.staff = staff;
        this.students = students;
        this.teacherClasses = teacherClasses;
        this.catalog = catalog;
        this.schedules = schedules;
        this.clock = clock;
    }

    /**
     * 범위 확인(활성 교사 → 담당 반, 관리자는 학원 전체)을 거친 반 — 생성자가 private이라 TeacherScope만 만든다. 명단·회차(roster·lessonsOf)는 이것만 받아,
     * 확인 없이 만든 TeacherClass로 같은 학원 다른 반 학생 명단을 얻는 길을 막는다. today = 그 학원 타임존 날짜
     */
    static final class ScopedClass {
        private final long academyId;
        private final TeacherClass clazz;
        private final LocalDate today;

        private ScopedClass(long academyId, TeacherClass clazz, LocalDate today) {
            this.academyId = academyId;
            this.clazz = clazz;
            this.today = today;
        }

        long academyId() {
            return academyId;
        }

        TeacherClass clazz() {
            return clazz;
        }

        LocalDate today() {
            return today;
        }
    }

    /** 진행 중 반, id 순(4.1). 순서: 활성 교사·관리자 확인(아니면 401) → 오늘(학원 타임존) → 범위(관리자 학원 전체, 교사 담당 반) */
    List<ScopedClass> activeClasses(long academyId, long actorId) {
        boolean wholeAcademy = wholeAcademy(academyId, actorId);
        LocalDate today = today(academyId);
        return teacherClasses.activeClasses(academyId, actorId, wholeAcademy, today).stream()
                .map(clazz -> new ScopedClass(academyId, clazz, today))
                .toList();
    }

    /** 반 하나. 순서는 activeClasses와 같고, 범위 밖·없는 반은 404 */
    ScopedClass classOf(long academyId, long actorId, long classId) {
        boolean wholeAcademy = wholeAcademy(academyId, actorId);
        LocalDate today = today(academyId);
        return new ScopedClass(
                academyId, teacherClasses.classOf(academyId, actorId, wholeAcademy, classId, today), today);
    }

    /** 요청한 사람이 지금 활성 관리자면 true, 활성 교사면 false. 둘 다 아니면 401(DB의 지금 역할 — JWT 역할 아님) */
    private boolean wholeAcademy(long academyId, long actorId) {
        return staff.requireActiveStaff(academyId, actorId) == StaffRole.ADMIN;
    }

    private LocalDate today(long academyId) {
        return LocalDate.now(clock.withZone(academies.zoneOf(academyId)));
    }

    /**
     * 반의 현재 소속 중 그 학원 학생만(이름 순). enrollment에는 학원 칸이 없어 소속이 어긋나면 다른 학원 학생·교사 id가 섞일 수 있다 — 교사 화면의 숫자·행·진도
     * 조회는 모두 이 목록만 쓰고, 빠진 id는 WARN으로 남긴다(화면은 일관되게, 원인은 로그로)
     */
    StudentRoster roster(ScopedClass scoped) {
        TeacherClass clazz = scoped.clazz();
        StudentRoster roster = students.roster(scoped.academyId(), clazz.studentIds());
        List<StudentAccount> found = roster.accounts();
        if (found.size() != clazz.studentIds().size()) {
            Set<Long> kept = found.stream().map(StudentAccount::id).collect(Collectors.toSet());
            log.warn(
                    "반 소속에 그 학원 학생이 아닌 id가 있다: class {} ids {}",
                    clazz.classId(),
                    clazz.studentIds().stream().filter(id -> !kept.contains(id)).toList());
        }
        return roster;
    }

    /**
     * 반 진도표(4.5). 진도표 api(ClassSchedules)는 범위 확인을 거친 반으로만 부르도록 여기서만 쓴다 — clazz는 학원 범위만 보고 담당 여부는 모른다
     */
    List<ScheduleEntry> schedule(ScopedClass scoped) {
        return schedules.schedule(scoped.academyId(), scoped.clazz().classId(), scoped.today());
    }

    /** 진도표 한 행 변경(4.6). today = 범위 확인 때 구한 학원 날짜 */
    List<ScheduleEntry> reschedule(ScopedClass scoped, int seq, ScheduleChange change) {
        return schedules.reschedule(scoped.academyId(), scoped.clazz().classId(), seq, change, scoped.today());
    }

    /** 휴강(4.7) */
    List<ScheduleEntry> cancel(ScopedClass scoped, int seq, String note, boolean shiftFollowing) {
        return schedules.cancel(
                scoped.academyId(), scoped.clazz().classId(), seq, note, shiftFollowing, scoped.today());
    }

    /** 반 권의 회차, 회차 id → 요약 */
    Map<Long, LessonSummary> lessonsOf(ScopedClass scoped) {
        return catalog.lessonsOfBook(scoped.clazz().bookId()).stream()
                .collect(Collectors.toMap(LessonSummary::lessonId, Function.identity()));
    }

    /**
     * 교사 화면(4.2·4.3)의 회차 진도 한 칸. 진도가 없으면 시작 전·0/지문 수. 저장된 전체 수가 지금 지문 수와 다르면(지문이 바뀐 회차를 다시 import한 뒤 그 학생이
     * 아직 다시 듣지 않음) 지금 지문 수를 쓰고 완료 수를 그 안으로 자른 뒤 WARN — 학생 화면(2.1·2.3)과 같은 규칙이라 교사와 학생이 같은 숫자를 본다. 상태는 저장값
     * 그대로(다음 청취가 다시 판정한다)
     */
    static LessonProgressSummary progressOf(LessonProgressSummary stored, LessonSummary lesson, long studentId) {
        int total = lesson.passageCount();
        if (stored == null) {
            return new LessonProgressSummary(LessonProgressStatus.NOT_STARTED, 0, total, null);
        }
        if (stored.requiredTotal() == total) {
            return stored;
        }
        log.warn(
                "회차 집계의 지문 수가 실제와 다르다: student {} lesson {} 집계 {} 실제 {} — 실제 지문 수로 보낸다",
                studentId,
                lesson.code(),
                stored.requiredTotal(),
                total);
        return new LessonProgressSummary(
                stored.status(), Math.min(stored.requiredDone(), total), total, stored.completedAt());
    }

    /** 진도표 회차의 요약. 그 반 권에 없으면 진도표 데이터 오류 */
    static LessonSummary summaryOf(Map<Long, LessonSummary> lessons, TeacherClass clazz, ClassLesson lesson) {
        LessonSummary summary = lessons.get(lesson.lessonId());
        if (summary == null) {
            throw new IllegalStateException(
                    "반 진도표 회차가 그 반 권에 없다: class " + clazz.classId() + " lesson " + lesson.lessonId());
        }
        return summary;
    }
}
