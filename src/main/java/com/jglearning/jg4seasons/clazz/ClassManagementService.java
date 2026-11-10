package com.jglearning.jg4seasons.clazz;

import com.jglearning.jg4seasons.academy.api.AcademyDirectory;
import com.jglearning.jg4seasons.clazz.api.ClassChange;
import com.jglearning.jg4seasons.clazz.api.ClassDetail;
import com.jglearning.jg4seasons.clazz.api.ClassManagement;
import com.jglearning.jg4seasons.clazz.api.ClassStatus;
import com.jglearning.jg4seasons.clazz.api.NewClass;
import com.jglearning.jg4seasons.clazz.persistence.AccessCodeLookup;
import com.jglearning.jg4seasons.clazz.persistence.ClassSessionStore;
import com.jglearning.jg4seasons.clazz.persistence.ClassSessionStore.NewSessionRow;
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
    private final AcademyDirectory academies;
    private final LicenseGuard license;
    private final Clock clock;

    ClassManagementService(
            ClassRepository classes,
            EnrollmentRepository enrollments,
            ClassSessionStore sessions,
            AccessCodeLookup accessCodes,
            AccessCodeGenerator codeGenerator,
            AcademyDirectory academies,
            LicenseGuard license,
            Clock clock) {
        this.classes = classes;
        this.enrollments = enrollments;
        this.sessions = sessions;
        this.accessCodes = accessCodes;
        this.codeGenerator = codeGenerator;
        this.academies = academies;
        this.license = license;
        this.clock = clock;
    }

    @Override
    public ClassDetail create(long academyId, NewClass newClass) {
        String name = cleanName(newClass.name());
        Weekdays weekdays = Weekdays.fromNumbers(newClass.weekdays());
        // 진도표 행 수 = 권의 회차 수(블랙 28, 그 밖 24 — ADR-27). BookDirectory가 1~회차 수를 검사해 넘긴다
        List<Long> lessonIds = newClass.lessons().lessonIdsByNumber();
        if (lessonIds.isEmpty()) {
            throw new IllegalArgumentException("회차 id가 없다");
        }
        // 입력 검사(400) 다음, 쓰기 전. 시작 전에는 개강 준비로 만들 수 있다(ADR 0031)
        license.requirePreparation(academyId, newClass.lessons().bookId(), today(academyId));

        Clazz clazz = classes.save(Clazz.create(
                academyId,
                newClass.lessons().bookId(),
                name,
                newClass.startDate(),
                weekdays,
                codeGenerator.next(accessCodes::exists),
                issuedAt()));

        List<LocalDate> dates = ClassSchedule.dates(newClass.startDate(), weekdays, lessonIds.size());
        List<NewSessionRow> rows = new ArrayList<>(lessonIds.size());
        for (int i = 0; i < lessonIds.size(); i++) {
            rows.add(new NewSessionRow(i + 1, lessonIds.get(i), dates.get(i)));
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
        boolean reactivating =
                change.status().filter(s -> s == ClassStatus.ACTIVE).isPresent()
                        && clazz.getStatus() != ClassStatus.ACTIVE;
        if (reactivating) {
            // 끝난 반을 되살리면 그 반 학생이 다시 학습한다 — 등록과 같은 사용권 확인(ADR 0031). 바꾸기 전에 본다
            license.requirePreparation(academyId, clazz.getBookId(), today(academyId));
            license.requireSeats(
                    academyId,
                    clazz.getBookId(),
                    enrollments.findByClassIdAndLeftAtIsNullOrderByStudentIdAsc(clazz.getId()).stream()
                            .map(Enrollment::getStudentId)
                            .toList());
        }
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

    /** 학원 타임존 오늘 */
    private LocalDate today(long academyId) {
        return LocalDate.now(clock.withZone(academies.zoneOf(academyId)));
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
