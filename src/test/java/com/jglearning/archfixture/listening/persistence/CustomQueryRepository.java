package com.jglearning.archfixture.listening.persistence;

import org.springframework.jdbc.core.simple.JdbcClient;

/** 정상: 모듈의 persistence 패키지에서는 JDBC를 직접 쓸 수 있다. */
public class CustomQueryRepository {
    private final JdbcClient jdbc;

    public CustomQueryRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Integer count() {
        return jdbc.sql("select 1").query(Integer.class).single();
    }
}
