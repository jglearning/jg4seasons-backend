package com.jglearning.jg4seasons.teacher;

import com.jglearning.jg4seasons.academy.api.AcademyDirectory;
import com.jglearning.jg4seasons.auth.api.StaffAccounts;
import com.jglearning.jg4seasons.auth.api.StaffRole;
import com.jglearning.jg4seasons.auth.api.StudentAccount;
import com.jglearning.jg4seasons.auth.api.StudentAccounts;
import com.jglearning.jg4seasons.clazz.api.ClassLesson;
import com.jglearning.jg4seasons.clazz.api.TeacherClass;
import com.jglearning.jg4seasons.clazz.api.TeacherClasses;
import com.jglearning.jg4seasons.content.api.LessonCatalog;
import com.jglearning.jg4seasons.content.api.LessonSummary;
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
    private final Clock clock;

    TeacherScope(
            AcademyDirectory academies,
            StaffAccounts staff,
            StudentAccounts students,
            TeacherClasses teacherClasses,
            LessonCatalog catalog,
            Clock clock) {
        this.academies = academies;
        this.staff = staff;
        this.students = students;
        this.teacherClasses = teacherClasses;
        this.catalog = catalog;
        this.clock = clock;
    }

    /** 범위 안 반 하나와 그 학원의 오늘 */
    record ScopedClass(TeacherClass clazz, LocalDate today) {}

    /** 범위 안 진행 중 반들과 그 학원의 오늘 */
    record ScopedClasses(List<TeacherClass> classes, LocalDate today) {}

    /** 진행 중 반, id 순(4.1). 순서: 활성 교사·관리자 확인(아니면 401) → 오늘(학원 타임존) → 범위(관리자 학원 전체, 교사 담당 반) */
    ScopedClasses activeClasses(long academyId, long actorId) {
        boolean wholeAcademy = wholeAcademy(academyId, actorId);
        LocalDate today = today(academyId);
        return new ScopedClasses(teacherClasses.activeClasses(academyId, actorId, wholeAcademy, today), today);
    }

    /** 반 하나. 순서는 activeClasses와 같고, 범위 밖·없는 반은 404 */
    ScopedClass classOf(long academyId, long actorId, long classId) {
        boolean wholeAcademy = wholeAcademy(academyId, actorId);
        LocalDate today = today(academyId);
        return new ScopedClass(teacherClasses.classOf(academyId, actorId, wholeAcademy, classId, today), today);
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
    List<StudentAccount> academyStudents(long academyId, TeacherClass clazz) {
        List<StudentAccount> found = students.findAll(academyId, clazz.studentIds());
        if (found.size() != clazz.studentIds().size()) {
            Set<Long> kept = found.stream().map(StudentAccount::id).collect(Collectors.toSet());
            log.warn(
                    "반 소속에 그 학원 학생이 아닌 id가 있다: class {} ids {}",
                    clazz.classId(),
                    clazz.studentIds().stream().filter(id -> !kept.contains(id)).toList());
        }
        return found;
    }

    /** 반 권의 회차, 회차 id → 요약 */
    Map<Long, LessonSummary> lessonsOf(TeacherClass clazz) {
        return catalog.lessonsOfBook(clazz.bookId()).stream()
                .collect(Collectors.toMap(LessonSummary::lessonId, Function.identity()));
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
