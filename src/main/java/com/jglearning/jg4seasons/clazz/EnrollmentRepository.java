package com.jglearning.jg4seasons.clazz;

import com.jglearning.jg4seasons.clazz.api.ClassStatus;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

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

    /**
     * 그 학원·그 권의 진행 중(active) 반에 지금 소속된 서로 다른 학생(사용권 학생 수, ADR 0031). 반 조건에 학원이 있어 다른 학원 소속은 세지 않는다. 끝난 반 학생은
     * 학습할 수 없어 세지 않는다
     */
    @Query("select distinct e.studentId from Enrollment e, Clazz c where c.id = e.classId and c.academyId = :academyId"
            + " and c.bookId = :bookId and c.status = :active and e.leftAt is null")
    List<Long> findCurrentStudentIdsOfBook(
            @Param("academyId") Long academyId, @Param("bookId") Long bookId, @Param("active") ClassStatus active);

    Enrollment save(Enrollment enrollment);
}
