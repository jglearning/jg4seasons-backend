package com.jglearning.jg4seasons.clazz;

import com.jglearning.jg4seasons.clazz.api.ClassDirectory;
import com.jglearning.jg4seasons.clazz.api.ClassEntry;
import com.jglearning.jg4seasons.clazz.persistence.AccessCodeLookup;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
class ClassDirectoryService implements ClassDirectory {

    private final AccessCodeLookup accessCodes;
    private final ClassRepository classes;
    private final EnrollmentRepository enrollments;

    ClassDirectoryService(AccessCodeLookup accessCodes, ClassRepository classes, EnrollmentRepository enrollments) {
        this.accessCodes = accessCodes;
        this.classes = classes;
        this.enrollments = enrollments;
    }

    @Override
    public Optional<ClassEntry> findActiveByAccessCode(String normalizedCode) {
        return accessCodes
                .findActiveClass(normalizedCode)
                .flatMap(match -> classes.findByAcademyIdAndId(match.academyId(), match.classId()))
                .filter(clazz -> clazz.getStatus() == ClassStatus.ACTIVE)
                .map(clazz -> new ClassEntry(clazz.getId(), clazz.getAcademyId(), clazz.getBookId(), clazz.getName()));
    }

    @Override
    public boolean isCurrentMember(long academyId, long classId, long studentId) {
        return classes.findByAcademyIdAndId(academyId, classId).isPresent()
                && enrollments.existsByClassIdAndStudentIdAndLeftAtIsNull(classId, studentId);
    }
}
