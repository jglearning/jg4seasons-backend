package com.jglearning.jg4seasons.clazz;

import org.springframework.data.repository.Repository;

/** 반을 학원 범위로 확인한 뒤에만 부른다(Enrollment 주석). */
interface EnrollmentRepository extends Repository<Enrollment, Long> {

    boolean existsByClassIdAndStudentIdAndLeftAtIsNull(Long classId, Long studentId);
}
