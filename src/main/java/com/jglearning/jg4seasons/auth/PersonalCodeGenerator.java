package com.jglearning.jg4seasons.auth;

import java.security.SecureRandom;
import java.util.function.Predicate;
import java.util.random.RandomGenerator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 개인 코드 뽑기: 숫자 4자리(0000~9999, 0으로 시작 가능). 순번이 아니라 SecureRandom — 이웃 학생 코드를 짐작하지 못하게(결정 A). 학원에 학생이
 * 많아져 겹치는 일이 잦아도 20번이면 충분하다(학생 1,000명이어도 20번 모두 겹칠 확률 1e-20).
 */
@Component
class PersonalCodeGenerator {

    static final int MAX_TRIES = 20;
    private static final int BOUND = 10_000;

    private final RandomGenerator random;

    @Autowired
    PersonalCodeGenerator() {
        this(new SecureRandom());
    }

    PersonalCodeGenerator(RandomGenerator random) {
        this.random = random;
    }

    /** taken이 참이면(이미 쓰는 코드) 다시 뽑는다. MAX_TRIES번 모두 겹치면 IllegalStateException(메시지에 코드 없음) */
    String next(Predicate<String> taken) {
        for (int i = 0; i < MAX_TRIES; i++) {
            String code = String.format("%04d", random.nextInt(BOUND));
            if (!taken.test(code)) {
                return code;
            }
        }
        throw new IllegalStateException("개인 코드를 " + MAX_TRIES + "번 뽑았는데 모두 이미 있다");
    }
}
