package com.jglearning.jg4seasons.clazz;

import com.jglearning.jg4seasons.clazz.api.ClassChange;
import com.jglearning.jg4seasons.clazz.api.ClassDetail;
import com.jglearning.jg4seasons.clazz.api.ClassManagement;
import com.jglearning.jg4seasons.clazz.api.ClassStatus;
import com.jglearning.jg4seasons.clazz.api.NewClass;
import com.jglearning.jg4seasons.clazz.persistence.AccessCodeLookup;
import com.jglearning.jg4seasons.clazz.persistence.ClassSessionStore;
import com.jglearning.jg4seasons.clazz.persistence.ClassSessionStore.SessionRow;
import com.jglearning.jg4seasons.infra.web.error.ApiException;
import com.jglearning.jg4seasons.infra.web.error.CommonErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
class ClassManagementService implements ClassManagement {

    private final ClassRepository classes;
    private final EnrollmentRepository enrollments;
    private final ClassSessionStore sessions;
    private final AccessCodeLookup accessCodes;
    private final AccessCodeGenerator codeGenerator;
    private final Clock clock;

    ClassManagementService(
            ClassRepository classes,
            EnrollmentRepository enrollments,
            ClassSessionStore sessions,
            AccessCodeLookup accessCodes,
            AccessCodeGenerator codeGenerator,
            Clock clock) {
        this.classes = classes;
        this.enrollments = enrollments;
        this.sessions = sessions;
        this.accessCodes = accessCodes;
        this.codeGenerator = codeGenerator;
        this.clock = clock;
    }

    @Override
    public ClassDetail create(long academyId, NewClass newClass) {
        String name = cleanName(newClass.name());
        Weekdays weekdays = Weekdays.fromNumbers(newClass.weekdays());
        List<Long> lessonIds = newClass.lessonIdsByNumber();
        if (lessonIds.size() != ClassSchedule.SESSION_COUNT) {
            throw new IllegalArgumentException("회차 id는 " + ClassSchedule.SESSION_COUNT + "개여야 한다: " + lessonIds.size());
        }

        Clazz clazz = classes.save(Clazz.create(
                academyId,
                newClass.bookId(),
                name,
                newClass.startDate(),
                weekdays,
                codeGenerator.next(accessCodes::exists),
                issuedAt()));

        List<LocalDate> dates = ClassSchedule.dates(newClass.startDate(), weekdays, ClassSchedule.SESSION_COUNT);
        List<SessionRow> rows = new ArrayList<>(ClassSchedule.SESSION_COUNT);
        for (int i = 0; i < ClassSchedule.SESSION_COUNT; i++) {
            rows.add(new SessionRow(i + 1, lessonIds.get(i), dates.get(i), "scheduled"));
        }
        sessions.insertScheduled(clazz.getId(), rows);
        return toDetail(clazz);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ClassDetail> list(long academyId, Optional<ClassStatus> status) {
        List<Clazz> found = status.map(s -> classes.findByAcademyIdAndStatusOrderById(academyId, s))
                .orElseGet(() -> classes.findByAcademyIdOrderById(academyId));
        return found.stream().map(this::toDetail).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ClassDetail find(long academyId, long classId) {
        return toDetail(classes.findByAcademyIdAndId(academyId, classId).orElseThrow(ClassManagementService::notFound));
    }

    @Override
    public ClassDetail change(long academyId, long classId, ClassChange change) {
        Clazz clazz = lockClass(academyId, classId);
        change.name().map(ClassManagementService::cleanName).ifPresent(clazz::rename);
        change.status().ifPresent(clazz::changeStatus);
        change.allowFutureLessons().ifPresent(clazz::allowFutureLessons);
        return toDetail(clazz);
    }

    @Override
    public ClassDetail regenerateAccessCode(long academyId, long classId) {
        Clazz clazz = lockClass(academyId, classId);
        clazz.reissueAccessCode(codeGenerator.next(accessCodes::exists), issuedAt());
        return toDetail(clazz);
    }

    private Clazz lockClass(long academyId, long classId) {
        return classes.findByAcademyIdAndIdForUpdate(academyId, classId).orElseThrow(ClassManagementService::notFound);
    }

    /** DATETIME은 초 단위 반올림 — 내림해 저장해 실제보다 늦은 시각이 되지 않게 */
    private Instant issuedAt() {
        return clock.instant().truncatedTo(ChronoUnit.SECONDS);
    }

    private static String cleanName(String raw) {
        String name = raw == null ? "" : raw.strip();
        if (name.isEmpty() || name.length() > NAME_MAX) {
            throw ApiException.validation("name", "반 이름은 1~" + NAME_MAX + "자예요");
        }
        return name;
    }

    private static ApiException notFound() {
        // 다른 학원 반·없는 반 — 존재를 드러내지 않게 404(API 명세 §0.3)
        return new ApiException(CommonErrorCode.NOT_FOUND, "반을 찾을 수 없어요");
    }

    private ClassDetail toDetail(Clazz clazz) {
        return new ClassDetail(
                clazz.getId(),
                clazz.getName(),
                clazz.getBookId(),
                clazz.getStartDate(),
                clazz.getWeekdays().numbers(),
                clazz.getAccessCode(),
                clazz.getCodeIssuedAt(),
                clazz.isAllowFutureLessons(),
                clazz.getStatus(),
                enrollments.countByClassIdAndLeftAtIsNull(clazz.getId()));
    }
}
