package com.jglearning.archfixture.listening;

import org.springframework.jdbc.core.JdbcTemplate;

/** 위반: 서비스가 JdbcTemplate으로 직접 쿼리한다(학원 조건 검사를 우회할 수 있음). */
public class RawJdbcService {
    private final JdbcTemplate jdbc;

    public RawJdbcService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Integer count() {
        return jdbc.queryForObject("select count(*) from user", Integer.class);
    }
}
