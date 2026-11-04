package com.jglearning.jg4seasons.auth.devseed;

import com.jglearning.jg4seasons.academy.api.AcademyDirectory;
import com.jglearning.jg4seasons.auth.AuthIdentityRepository;
import com.jglearning.jg4seasons.auth.PersonalCodeHasher;
import com.jglearning.jg4seasons.auth.UserRepository;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** local 프로파일에서 개발 시드(DevSeedRunner, @Order(0)) 다음에 데모 학생 개인 코드를 넣는다. 운영·테스트에서는 빈이 없다. */
@Component
@Profile("local")
@Order(1)
public class AuthDevSeedRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AuthDevSeedRunner.class);

    private final AuthDevSeed seed;

    public AuthDevSeedRunner(
            AcademyDirectory academies,
            UserRepository users,
            AuthIdentityRepository identities,
            PersonalCodeHasher hasher,
            Clock clock) {
        this.seed = new AuthDevSeed(academies, users, identities, hasher, clock);
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        log.info("dev seed: personal codes inserted={}", seed.seed()); // 개수만(코드 값은 남기지 않음)
    }
}
