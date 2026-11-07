package com.jglearning.jg4seasons.infra.persistence.devseed;

import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * local·trial 프로파일에서 서버가 뜰 때 데모 데이터를 넣는다. 테스트·운영(prod만)에서는 빈이 만들어지지 않는다.
 *
 * <p>trial은 운영 시험용(운영 프로파일 prod에 파라미터 SPRING_PROFILES_INCLUDE=trial로 더한다 — 실제 사용 전 운영 DB를 다시 만든다). 가입 코드는 Parameter Store 값이어야 하고,
 * 아니면 이 빈을 만들 때 서버가 멈춘다(SeedAccessCode).
 */
@Component
@Profile({"local", "trial"})
@Order(0) // 첫 학원 부트스트랩(@Order(-10)) 다음, 인증 시드(AuthDevSeedRunner, @Order(1)) 전
public class DevSeedRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevSeedRunner.class);

    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final String accessCode;

    public DevSeedRunner(
            JdbcTemplate jdbc,
            Clock clock,
            Environment environment,
            @Value("${" + SeedAccessCode.KEY + ":}") String configuredAccessCode) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.accessCode = SeedAccessCode.resolve(configuredAccessCode, environment.matchesProfiles("trial"));
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        log.info("dev seed: demo academy inserted={}", new DevSeed(jdbc, clock, accessCode).seed()); // 코드 값은 남기지 않음
        // 회차 내용은 학원과 따로 건너뛴다 — 이미 데모 학원이 있는 로컬 DB에도 들어간다
        new ContentDevSeed(jdbc).seed();
    }
}
