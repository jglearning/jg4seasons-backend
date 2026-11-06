package com.jglearning.jg4seasons.clazz;

import com.jglearning.jg4seasons.clazz.api.ClassLesson;
import com.jglearning.jg4seasons.clazz.api.ClassStatus;
import com.jglearning.jg4seasons.clazz.api.LessonAccess;
import com.jglearning.jg4seasons.clazz.api.StudentClass;
import com.jglearning.jg4seasons.clazz.api.StudentClasses;
import com.jglearning.jg4seasons.clazz.persistence.ClassSessionStore;
import com.jglearning.jg4seasons.clazz.persistence.ClassSessionStore.SessionRow;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
class StudentClassesService implements StudentClasses {

    private static final Logger log = LoggerFactory.getLogger(StudentClassesService.class);

    private final EnrollmentRepository enrollments;
    private final ClassRepository classes;
    private final ClassSessionStore sessions;

    StudentClassesService(EnrollmentRepository enrollments, ClassRepository classes, ClassSessionStore sessions) {
        this.enrollments = enrollments;
        this.classes = classes;
        this.sessions = sessions;
    }

    @Override
    public Optional<StudentClass> currentClass(long academyId, long studentId) {
        return currentClasses(academyId, studentId).stream().findFirst();
    }

    @Override
    public List<StudentClass> currentClasses(long academyId, long studentId) {
        // enrollment에는 academy_id가 없다 — 반은 학원 범위 조회로 거른다
        List<Long> classIds = enrollments.findByStudentIdAndLeftAtIsNull(studentId).stream()
                .map(Enrollment::getClassId)
                .toList();
        if (classIds.isEmpty()) {
            return List.of();
        }
        return classes.findByAcademyIdAndIdIn(academyId, classIds).stream()
                .filter(clazz -> clazz.getStatus() == ClassStatus.ACTIVE)
                .sorted(Comparator.comparing(Clazz::getId))
                .map(clazz -> new StudentClass(
                        clazz.getId(),
                        clazz.getAcademyId(),
                        clazz.getBookId(),
                        clazz.getName(),
                        clazz.isAllowFutureLessons()))
                .toList();
    }

    @Override
    public List<ClassLesson> lessons(StudentClass clazz, LocalDate today) {
        Map<Long, List<SessionRow>> byLesson = sessions.findByClass(clazz.classId()).stream()
                .collect(Collectors.groupingBy(SessionRow::lessonId, LinkedHashMap::new, Collectors.toList()));
        return byLesson.values().stream()
                .map(rows -> fold(clazz.classId(), rows, today, clazz.allowFutureLessons()))
                .sorted(Comparator.comparingInt(ClassLesson::seq))
                .toList();
    }

    /** 휴강이 아닌 행 중 seq가 가장 작은 것을 대표로(없으면 휴강 행). reached는 휴강이 아닌 행만, open은 LessonOpening */
    private static ClassLesson fold(long classId, List<SessionRow> rows, LocalDate today, boolean allowFuture) {
        List<SessionRow> held = rows.stream()
                .filter(row -> !LessonOpening.CANCELLED.equals(row.status()))
                .sorted(Comparator.comparingInt(SessionRow::seq))
                .toList();
        if (held.size() > 1) {
            // 휴강 시프트는 옛 행을 cancelled로 남기고 새 행을 하나 만든다. 둘 이상이면 진도표 편집 오류 — seq가 가장 작은 행을 쓴다
            log.warn(
                    "한 회차에 휴강이 아닌 수업 행이 둘 이상이다: class {} lesson {} seq {}",
                    classId,
                    held.get(0).lessonId(),
                    held.stream().map(SessionRow::seq).toList());
        }
        SessionRow representative = held.isEmpty()
                ? rows.stream().min(Comparator.comparingInt(SessionRow::seq)).orElseThrow()
                : held.get(0);
        boolean reached = held.stream().anyMatch(row -> !row.scheduledDate().isAfter(today));
        return new ClassLesson(
                representative.lessonId(),
                representative.seq(),
                representative.scheduledDate(),
                reached,
                LessonOpening.isOpen(rows, today, allowFuture),
                !held.isEmpty());
    }

    @Override
    public LessonAccess access(StudentClass clazz, long lessonId, LocalDate today) {
        List<SessionRow> rows = sessions.findByClassAndLesson(clazz.classId(), lessonId);
        if (rows.isEmpty()) {
            return LessonAccess.NOT_IN_CLASS;
        }
        return LessonOpening.isOpen(rows, today, clazz.allowFutureLessons()) ? LessonAccess.OPEN : LessonAccess.LOCKED;
    }
}
