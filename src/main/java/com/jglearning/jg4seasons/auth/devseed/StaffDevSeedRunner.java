package com.jglearning.jg4seasons.auth.devseed;

import com.jglearning.jg4seasons.academy.api.AcademyDirectory;
import com.jglearning.jg4seasons.auth.AuthIdentityRepository;
import com.jglearning.jg4seasons.auth.UserRepository;
import com.jglearning.jg4seasons.auth.api.StaffAccounts;
import com.jglearning.jg4seasons.clazz.api.ClassDirectory;
import com.jglearning.jg4seasons.clazz.api.ClassTeachers;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * local 프로파일에서만 데모 관리자·교사 로그인을 넣는다. 비밀번호가 공개 저장소에 평문으로 있어(StaffDevSeed) 운영 시험(trial)에서도 돌지 않는다 — 운영
 * 관리자는 첫 학원 부트스트랩이 만든 계정뿐이다.
 */
@Component
@Profile("local")
@Order(2)
public class StaffDevSeedRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StaffDevSeedRunner.class);

    private final StaffDevSeed seed;

    public StaffDevSeedRunner(
            AcademyDirectory academies,
            UserRepository users,
            AuthIdentityRepository identities,
            PasswordEncoder passwordEncoder,
            StaffAccounts accounts,
            ClassDirectory classes,
            ClassTeachers classTeachers,
            Clock clock) {
        this.seed = new StaffDevSeed(
                academies, users, identities, passwordEncoder, accounts, classes, classTeachers, clock);
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        log.info("dev seed: staff logins inserted={}", seed.seed()); // 개수만(비밀번호는 남기지 않음)
    }
}
