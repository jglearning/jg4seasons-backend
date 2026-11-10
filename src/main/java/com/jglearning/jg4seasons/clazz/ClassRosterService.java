package com.jglearning.jg4seasons.clazz;

import com.jglearning.jg4seasons.academy.api.BookLicenses;
import com.jglearning.jg4seasons.clazz.api.ActiveClassToday;
import com.jglearning.jg4seasons.clazz.api.ClassLesson;
import com.jglearning.jg4seasons.clazz.api.ClassRef;
import com.jglearning.jg4seasons.clazz.api.ClassRoster;
import com.jglearning.jg4seasons.clazz.api.ClassStatus;
import com.jglearning.jg4seasons.clazz.api.MoveResult;
import com.jglearning.jg4seasons.clazz.api.StudentClass;
import com.jglearning.jg4seasons.clazz.api.StudentClasses;
import com.jglearning.jg4seasons.clazz.api.TodayLesson;
import com.jglearning.jg4seasons.clazz.persistence.ClassTeacherStore;
import com.jglearning.jg4seasons.infra.web.error.ApiException;
import com.jglearning.jg4seasons.infra.web.error.CommonErrorCode;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 반 소속 쓰기·명단 조회. enrollment에는 academy_id가 없다 — 반 id로 찾을 때는 반을 학원 범위로 먼저 확인하고, 학생 id로 찾은 소속은 반을 학원 범위로 다시
 * 거른다(다른 학원 반 소속은 보지도 끝내지도 않는다).
 */
@Service
@Transactional
class ClassRosterService implements ClassRoster {

    private final ClassRepository classes;
    private final EnrollmentRepository enrollments;
    private final ClassTeacherStore classTeachers;
    private final StudentClasses studentClasses;
    private final BookLicenses licenses;

    ClassRosterService(
            ClassRepository classes,
            EnrollmentRepository enrollments,
            ClassTeacherStore classTeachers,
            StudentClasses studentClasses,
            BookLicenses licenses) {
        this.classes = classes;
        this.enrollments = enrollments;
        this.classTeachers = classTeachers;
        this.studentClasses = studentClasses;
        this.licenses = licenses;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Long> currentStudentIds(long academyId, long classId) {
        requireClass(academyId, classId);
        return studentIdsOf(classId);
    }

    @Override
    public void enroll(long academyId, long classId, List<Long> studentIds, LocalDate on) {
        Clazz clazz = requireClass(academyId, classId);
        requireActive(clazz);
        requireLicense(clazz, studentIds, on);
        for (Long studentId : studentIds) {
            enrollments.save(Enrollment.join(classId, studentId, on));
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, ClassRef> currentClassOf(long academyId, Collection<Long> studentIds) {
        if (studentIds.isEmpty()) {
            return Map.of();
        }
        List<Enrollment> current = enrollments.findByStudentIdInAndLeftAtIsNull(studentIds);
        Map<Long, Clazz> inAcademy = classesInAcademy(academyId, current);
        return current.stream()
                .filter(e -> inAcademy.containsKey(e.getClassId()))
                .collect(Collectors.toMap(Enrollment::getStudentId, e -> e.getClassId(), Math::min))
                .entrySet()
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> {
                    Clazz clazz = inAcademy.get(entry.getValue());
                    return new ClassRef(clazz.getId(), clazz.getName());
                }));
    }

    @Override
    public List<Long> leaveAll(long academyId, long studentId, LocalDate on) {
        List<Enrollment> current = currentInAcademy(academyId, studentId);
        current.forEach(e -> e.leave(on));
        return current.stream().map(Enrollment::getClassId).sorted().toList();
    }

    @Override
    public MoveResult move(long academyId, long studentId, long toClassId, LocalDate on) {
        Clazz target = requireClass(academyId, toClassId);
        requireActive(target);
        requireLicense(target, List.of(studentId), on);
        List<Enrollment> current = currentInAcademy(academyId, studentId);
        List<Enrollment> leaving =
                current.stream().filter(e -> e.getClassId() != toClassId).toList();
        boolean alreadyThere = leaving.size() < current.size();
        if (alreadyThere && leaving.isEmpty()) {
            throw new ApiException(ClassErrorCode.ALREADY_IN_CLASS, "이미 그 반 학생이에요");
        }
        leaving.forEach(e -> e.leave(on));
        if (!alreadyThere) {
            enrollments.save(Enrollment.join(toClassId, studentId, on));
        }
        return new MoveResult(
                leaving.stream().map(Enrollment::getClassId).sorted().toList(), toClassId);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isTaughtBy(long academyId, long studentId, long teacherId) {
        Set<Long> taught = new HashSet<>(classTeachers.classIdsOf(academyId, teacherId));
        return currentInAcademy(academyId, studentId).stream().anyMatch(e -> taught.contains(e.getClassId()));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isCurrentMemberOfAny(long academyId, long studentId) {
        return !currentInAcademy(academyId, studentId).isEmpty();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ActiveClassToday> activeClassesToday(long academyId, LocalDate today) {
        return classes.findByAcademyIdAndStatusOrderById(academyId, ClassStatus.ACTIVE).stream()
                .map(clazz -> {
                    // 학원 범위로 찾은 반이라 StudentClasses.lessons의 전제(학원 확인이 끝난 반)를 만족한다
                    List<ClassLesson> lessons = studentClasses.lessons(
                            new StudentClass(
                                    clazz.getId(),
                                    clazz.getAcademyId(),
                                    clazz.getBookId(),
                                    clazz.getName(),
                                    clazz.isAllowFutureLessons()),
                            today);
                    return new ActiveClassToday(
                            clazz.getId(),
                            clazz.getName(),
                            clazz.getBookId(),
                            TodayLesson.of(lessons),
                            studentIdsOf(clazz.getId()));
                })
                .toList();
    }

    private List<Long> studentIdsOf(long classId) {
        return enrollments.findByClassIdAndLeftAtIsNullOrderByStudentIdAsc(classId).stream()
                .map(Enrollment::getStudentId)
                .toList();
    }

    /** 학생의 현재 소속 중 그 학원 반만(반 id 순) */
    private List<Enrollment> currentInAcademy(long academyId, long studentId) {
        List<Enrollment> current = enrollments.findByStudentIdAndLeftAtIsNull(studentId);
        Map<Long, Clazz> inAcademy = classesInAcademy(academyId, current);
        return current.stream()
                .filter(e -> inAcademy.containsKey(e.getClassId()))
                .sorted(Comparator.comparing(Enrollment::getClassId))
                .toList();
    }

    private Map<Long, Clazz> classesInAcademy(long academyId, List<Enrollment> enrollmentsOf) {
        if (enrollmentsOf.isEmpty()) {
            return Map.of();
        }
        Set<Long> classIds = enrollmentsOf.stream().map(Enrollment::getClassId).collect(Collectors.toSet());
        return classes.findByAcademyIdAndIdIn(academyId, classIds).stream()
                .collect(Collectors.toMap(Clazz::getId, Function.identity(), (a, b) -> a, TreeMap::new));
    }

    private Clazz requireClass(long academyId, long classId) {
        // 다른 학원 반·없는 반 — 존재를 드러내지 않게 404(API 명세 §0.3)
        return classes.findByAcademyIdAndId(academyId, classId)
                .orElseThrow(() -> new ApiException(CommonErrorCode.NOT_FOUND, "반을 찾을 수 없어요"));
    }

    /**
     * 받는 반의 권 사용권(ADR 0031): 시작 전·기간 안이 아니면 403, (그 권 active 반의 현재 학생 ∪ 들어올 학생) 수가 한도를 넘으면 409. 합집합이라 같은 권 반끼리
     * 이동은 자리를 더 쓰지 않는다. 등록·이동은 학원 잠금(lockForAdminWrite) 안에서만 불리므로 동시 등록도 한도를 넘지 않는다
     */
    private void requireLicense(Clazz clazz, List<Long> incoming, LocalDate on) {
        long academyId = clazz.getAcademyId();
        long bookId = clazz.getBookId();
        if (!licenses.statusOf(academyId, bookId, on).allowsPreparation()) {
            throw new ApiException(ClassErrorCode.LICENSE_INACTIVE, "사용 기간이 아니에요");
        }
        OptionalInt limit = licenses.seatLimit(academyId, bookId);
        if (limit.isEmpty()) {
            return;
        }
        Set<Long> seated =
                new HashSet<>(enrollments.findCurrentStudentIdsOfBook(academyId, bookId, ClassStatus.ACTIVE));
        seated.addAll(incoming);
        if (seated.size() > limit.getAsInt()) {
            throw new ApiException(ClassErrorCode.LICENSE_SEATS_FULL, "사용할 수 있는 학생 수를 넘었어요");
        }
    }

    private static void requireActive(Clazz clazz) {
        if (clazz.getStatus() != ClassStatus.ACTIVE) {
            throw new ApiException(ClassErrorCode.CLASS_NOT_ACTIVE, "진행 중인 반에만 학생을 넣을 수 있어요");
        }
    }
}
