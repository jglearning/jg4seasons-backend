package com.jglearning.jg4seasons.infra.persistence.devseed;

import java.time.Clock;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** local 프로파일에서 서버가 뜰 때 개발용 시드를 넣는다. 운영·테스트에서는 빈이 만들어지지 않는다. */
@Component
@Profile("local")
public class DevSeedRunner implements ApplicationRunner {

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public DevSeedRunner(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        new DevSeed(jdbc, clock).seed();
    }
}
