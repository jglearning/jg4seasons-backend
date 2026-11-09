package com.jglearning.jg4seasons.clazz;

import java.util.Collection;
import java.util.List;
import org.springframework.data.repository.Repository;

/** 반 id로 찾는 메서드는 반을 학원 범위로 확인한 뒤에만 부른다(Enrollment 주석). 학생 id로 찾는 메서드는 결과 반을 학원 범위로 다시 거른다 */
interface EnrollmentRepository extends Repository<Enrollment, Long> {

    boolean existsByClassIdAndStudentIdAndLeftAtIsNull(Long classId, Long studentId);

    /** 현재 소속(left_at 없음) 학생 수 */
    int countByClassIdAndLeftAtIsNull(Long classId);

    /** 학생의 현재 소속 전부. 반은 학원 범위 조회(ClassRepository)로 다시 거른다 */
    List<Enrollment> findByStudentIdAndLeftAtIsNull(Long studentId);

    /** 반의 현재 소속. 반을 학원 범위로 확인한 뒤에만 */
    List<Enrollment> findByClassIdAndLeftAtIsNullOrderByStudentIdAsc(Long classId);

    /** 여러 학생의 현재 소속. 반은 학원 범위 조회로 다시 거른다 */
    List<Enrollment> findByStudentIdInAndLeftAtIsNull(Collection<Long> studentIds);

    Enrollment save(Enrollment enrollment);
}
