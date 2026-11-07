package com.jglearning.jg4seasons.infra.persistence.devseed;

import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * local 프로파일은 공개 저장소에 있는 비밀번호·가입 코드로 계정을 만든다(StaffDevSeed·DevSeed). 운영(prod)과 함께 켜지면 — 예: 파라미터
 * SPRING_PROFILES_INCLUDE에 trial 대신 local — 시드가 돌기 전에(빈 생성 단계) 서버 시작을 멈춘다.
 */
@Component
@Profile("local")
public class LocalProfileGuard {

    public LocalProfileGuard(Environment environment) {
        if (environment.matchesProfiles("prod")) {
            throw new IllegalStateException(
                    "운영(prod)에서 local 프로파일을 켤 수 없다. 시험 데이터는 SPRING_PROFILES_INCLUDE=trial로만 켠다");
        }
    }
}
