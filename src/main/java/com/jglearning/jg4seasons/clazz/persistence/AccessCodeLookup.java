package com.jglearning.jg4seasons.clazz.persistence;

import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 가입 코드 → 활성 반, 가입 코드 존재 확인. 학원 조건 없이 반을 찾는 유일한 조회다.
 *
 * <p>로그인 전에는 학원을 알 수 없고, 가입 코드(class.access_code)는 모든 학원을 통틀어 UNIQUE다. 반환은 반 id와 그 반의 학원 id뿐이고, 이후 조회는
 * 그 학원 id로 학원 범위 리포지토리를 쓴다(ClassDirectoryService). 학원 범위 리포지토리 규칙(academyId 첫 파라미터)을 우회하는 곳이라
 * persistence 패키지에 두고 이 한 곳으로 한정한다.
 */
@Component
public class AccessCodeLookup {

    public record Match(long classId, long academyId) {}

    private final JdbcTemplate jdbc;

    public AccessCodeLookup(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Match> findActiveClass(String normalizedCode) {
        List<Match> rows = jdbc.query(
                "select id, academy_id from `class` where access_code = ? and status = 'active'",
                (rs, i) -> new Match(rs.getLong("id"), rs.getLong("academy_id")),
                normalizedCode);
        return rows.stream().findFirst();
    }

    /** 상태·학원과 무관하게 이 가입 코드를 쓰는 반이 있는지(새 코드 발급 때 겹침 확인) */
    public boolean exists(String code) {
        Boolean exists =
                jdbc.queryForObject("select exists(select 1 from `class` where access_code = ?)", Boolean.class, code);
        return Boolean.TRUE.equals(exists);
    }
}
