package com.jglearning.jg4seasons.auth.persistence;

import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * refresh → 사용자. 학원 조건 없이 사용자를 읽는 유일한 조회다.
 *
 * <p>refresh 쿠키는 난수뿐이라 학원을 알 수 없다(쿠키 형식은 설계서 그대로). 찾는 키는 refresh_token.user_id이고 그 행은 쿠키 해시로만 찾을 수
 * 있어, 요청이 다른 사용자를 고를 방법이 없다. 돌려준 academyId로 새 access(JWT aca)를 만들고, 이후 조회는 그 학원 범위로 한다. 학원 범위 리포지토리
 * 규칙(academyId 첫 파라미터)을 우회하는 곳이라 persistence 패키지의 이 한 곳으로 한정한다.
 */
@Component
public class RefreshOwnerLookup {

    private final JdbcTemplate jdbc;

    public RefreshOwnerLookup(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 활성(status = active) 사용자만 */
    public Optional<RefreshOwner> findActiveOwner(long userId) {
        List<RefreshOwner> rows = jdbc.query(
                "select id, academy_id, role, name from `user` where id = ? and status = 'active'",
                (rs, i) -> new RefreshOwner(
                        rs.getLong("id"),
                        rs.getObject("academy_id", Long.class),
                        rs.getString("role"),
                        rs.getString("name")),
                userId);
        return rows.stream().findFirst();
    }
}
