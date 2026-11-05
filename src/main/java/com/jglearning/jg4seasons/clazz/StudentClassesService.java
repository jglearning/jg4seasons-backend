package com.jglearning.jg4seasons.clazz;

import com.jglearning.jg4seasons.clazz.api.ClassStatus;
import com.jglearning.jg4seasons.clazz.api.LessonAccess;
import com.jglearning.jg4seasons.clazz.api.StudentClass;
import com.jglearning.jg4seasons.clazz.api.StudentClasses;
import com.jglearning.jg4seasons.clazz.persistence.ClassSessionStore;
import com.jglearning.jg4seasons.clazz.persistence.ClassSessionStore.SessionRow;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
class StudentClassesService implements StudentClasses {

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
        // enrollment에는 academy_id가 없다 — 반은 학원 범위 조회로 거른다
        List<Long> classIds = enrollments.findByStudentIdAndLeftAtIsNull(studentId).stream()
                .map(Enrollment::getClassId)
                .toList();
        if (classIds.isEmpty()) {
            return Optional.empty();
        }
        return classes.findByAcademyIdAndIdIn(academyId, classIds).stream()
                .filter(clazz -> clazz.getStatus() == ClassStatus.ACTIVE)
                .min(Comparator.comparing(Clazz::getId))
                .map(clazz -> new StudentClass(
                        clazz.getId(), clazz.getAcademyId(), clazz.getBookId(), clazz.isAllowFutureLessons()));
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
