package com.jglearning.jg4seasons.clazz;

import com.jglearning.jg4seasons.clazz.api.ClassLesson;
import com.jglearning.jg4seasons.clazz.api.ClassStatus;
import com.jglearning.jg4seasons.clazz.api.SessionStatus;
import com.jglearning.jg4seasons.clazz.api.StudentClass;
import com.jglearning.jg4seasons.clazz.api.StudentClasses;
import com.jglearning.jg4seasons.clazz.api.TeacherClass;
import com.jglearning.jg4seasons.clazz.api.TeacherClasses;
import com.jglearning.jg4seasons.clazz.persistence.ClassSessionStore;
import com.jglearning.jg4seasons.clazz.persistence.ClassSessionStore.SessionRow;
import com.jglearning.jg4seasons.clazz.persistence.ClassTeacherStore;
import com.jglearning.jg4seasons.infra.web.error.ApiException;
import com.jglearning.jg4seasons.infra.web.error.CommonErrorCode;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 교사 화면이 보는 반. 반은 학원 범위 조회(ClassRepository)로만 찾고, 담당 여부는 학원 범위로 거른 담당 반 id(ClassTeacherStore)로 본다. 진도표 접기는
 * StudentClasses.lessons를 그대로 쓴다(학원 범위로 찾은 반이라 그 전제를 만족 — ClassRosterService.activeClassesToday와 같음).
 */
@Service
@Transactional(readOnly = true)
class TeacherClassesService implements TeacherClasses {

    private final ClassRepository classes;
    private final EnrollmentRepository enrollments;
    private final ClassTeacherStore classTeachers;
    private final ClassSessionStore sessions;
    private final StudentClasses studentClasses;

    TeacherClassesService(
            ClassRepository classes,
            EnrollmentRepository enrollments,
            ClassTeacherStore classTeachers,
            ClassSessionStore sessions,
            StudentClasses studentClasses) {
        this.classes = classes;
        this.enrollments = enrollments;
        this.classTeachers = classTeachers;
        this.sessions = sessions;
        this.studentClasses = studentClasses;
    }

    @Override
    public List<TeacherClass> activeClasses(long academyId, long teacherId, boolean wholeAcademy, LocalDate today) {
        List<Clazz> found = wholeAcademy
                ? classes.findByAcademyIdAndStatusOrderById(academyId, ClassStatus.ACTIVE)
                : classes.findByAcademyIdAndIdIn(academyId, classTeachers.classIdsOf(academyId, teacherId)).stream()
                        .filter(clazz -> clazz.getStatus() == ClassStatus.ACTIVE)
                        .sorted(Comparator.comparing(Clazz::getId))
                        .toList();
        return found.stream().map(clazz -> toTeacherClass(clazz, today)).toList();
    }

    @Override
    public TeacherClass classOf(long academyId, long teacherId, boolean wholeAcademy, long classId, LocalDate today) {
        Clazz clazz = classes.findByAcademyIdAndId(academyId, classId)
                .filter(found -> wholeAcademy
                        || classTeachers.classIdsOf(academyId, teacherId).contains(classId))
                .orElseThrow(() -> new ApiException(CommonErrorCode.NOT_FOUND, "반을 찾을 수 없어요"));
        return toTeacherClass(clazz, today);
    }

    private TeacherClass toTeacherClass(Clazz clazz, LocalDate today) {
        List<ClassLesson> lessons = studentClasses.lessons(
                new StudentClass(
                        clazz.getId(),
                        clazz.getAcademyId(),
                        clazz.getBookId(),
                        clazz.getName(),
                        clazz.isAllowFutureLessons()),
                today);
        // 대표 행 = 접은 회차의 seq 행. done은 저장값이 아니라 날짜로(진도표 4.5와 같은 판정)
        Set<Integer> doneSeqs = sessions.findByClass(clazz.getId()).stream()
                .filter(row -> SessionStatusRule.shown(row.status(), row.scheduledDate(), today) == SessionStatus.DONE)
                .map(SessionRow::seq)
                .collect(Collectors.toSet());
        Set<Long> doneLessonIds = lessons.stream()
                .filter(lesson -> doneSeqs.contains(lesson.seq()))
                .map(ClassLesson::lessonId)
                .collect(Collectors.toSet());
        List<Long> studentIds = enrollments.findByClassIdAndLeftAtIsNullOrderByStudentIdAsc(clazz.getId()).stream()
                .map(Enrollment::getStudentId)
                .toList();
        return new TeacherClass(
                clazz.getId(),
                clazz.getName(),
                clazz.getBookId(),
                clazz.getStatus(),
                lessons,
                doneLessonIds,
                studentIds);
    }
}
