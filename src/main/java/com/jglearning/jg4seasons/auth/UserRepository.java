package com.jglearning.jg4seasons.auth;

import com.jglearning.jg4seasons.infra.persistence.AcademyScopedRepository;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends AcademyScopedRepository<User, Long> {

    Optional<User> findByAcademyIdAndId(Long academyId, Long id);

    List<User> findByAcademyIdAndRoleOrderByIdAsc(Long academyId, UserRole role);

    List<User> findByAcademyIdAndRoleInOrderByIdAsc(Long academyId, Collection<UserRole> roles);

    /**
     * 사용자 행을 잠그고 읽는다(SELECT … FOR UPDATE). 같은 교사 계정을 동시에 바꾸는 요청을 줄 세운다 — 담당 반 교체(delete → insert)가 동시에
     * 돌면 MySQL gap lock 교착으로 500이 났다
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.academyId = :academyId and u.id = :id")
    Optional<User> findByAcademyIdAndIdForUpdate(@Param("academyId") Long academyId, @Param("id") Long id);

    /** 학생 명단(이름 순, 같으면 id 순). 학원 밖 id는 빠진다 */
    List<User> findByAcademyIdAndRoleAndIdInOrderByNameAscIdAsc(Long academyId, UserRole role, Collection<Long> ids);

    /** 이름 부분 일치. Containing은 Spring Data가 %·_를 이스케이프한다(글자 그대로 — 결정 E) */
    List<User> findTop50ByAcademyIdAndRoleAndNameContainingOrderByNameAscIdAsc(
            Long academyId, UserRole role, String namePart);

    @Query("select u.id from User u where u.academyId = :academyId and u.role = :role")
    List<Long> findIdsByAcademyIdAndRole(@Param("academyId") Long academyId, @Param("role") UserRole role);

    User save(User user);
}
