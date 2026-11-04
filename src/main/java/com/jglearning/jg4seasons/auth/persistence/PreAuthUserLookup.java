package com.jglearning.jg4seasons.auth.persistence;

import com.jglearning.jg4seasons.auth.UserRole;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 학원을 모르는 상태에서 사용자 id로 활성 사용자를 읽는다. 학원 조건 없이 사용자를 읽는 유일한 조회다.
 *
 * <p>쓰는 곳은 둘이다. refresh: 쿠키는 난수뿐이라 학원을 알 수 없고, 사용자 id는 쿠키 해시로 찾은 refresh_token 행에서 온다. 교사 로그인:
 * 로그인 아이디는 전체 학원에서 고유(UNIQUE(provider, identifier))라 아이디로 찾은 auth_identity 행에서 온다. 어느 쪽도 요청이 다른 사용자를
 * 고를 방법이 없다. 돌려준 academyId로 access(JWT aca)를 만들고, 이후 조회는 그 학원 범위로 한다. 학원 범위 리포지토리 규칙을 우회하는 곳이라
 * persistence 패키지의 이 한 곳으로 한정한다.
 */
@Component
public class PreAuthUserLookup {

    private final JdbcTemplate jdbc;

    public PreAuthUserLookup(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 활성(status = active) 사용자만 */
    public Optional<AuthUser> findActiveUser(long userId) {
        List<AuthUser> rows = jdbc.query(
                "select id, academy_id, role, name from `user` where id = ? and status = 'active'",
                (rs, i) -> new AuthUser(
                        rs.getLong("id"),
                        rs.getObject("academy_id", Long.class),
                        UserRole.fromValue(rs.getString("role")),
                        rs.getString("name")),
                userId);
        return rows.stream().findFirst();
    }
}
