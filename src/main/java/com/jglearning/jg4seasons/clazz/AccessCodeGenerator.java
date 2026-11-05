package com.jglearning.jg4seasons.clazz;

import java.security.SecureRandom;
import java.util.function.Predicate;
import java.util.random.RandomGenerator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 반 가입 코드(ADM-03). 8자, 대문자·숫자에서 혼동 문자(0·O·1·I·L)를 뺀 31자(31^8 ≈ 8.5×10^11). 가입 코드는 전체 학원에서 고유라 이미 있는 코드면 다시
 * 뽑는다. 확인과 저장 사이에 같은 코드가 동시에 뽑히는 경우는 UNIQUE 위반(500)으로 둔다 — 확률이 무시할 만하고, 저장 실패를 잡아 재시도하면 트랜잭션이 이미
 * rollback-only라 쓸 수 없다.
 */
@Component
class AccessCodeGenerator {

    static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    static final int LENGTH = 8;
    static final int MAX_TRIES = 5;

    private final RandomGenerator random;

    @Autowired
    AccessCodeGenerator() {
        this(new SecureRandom());
    }

    AccessCodeGenerator(RandomGenerator random) {
        this.random = random;
    }

    /** taken이 참이면(이미 있는 코드) 다시 뽑는다. MAX_TRIES번 모두 겹치면 IllegalStateException */
    String next(Predicate<String> taken) {
        for (int i = 0; i < MAX_TRIES; i++) {
            String code = draw();
            if (!taken.test(code)) {
                return code;
            }
        }
        throw new IllegalStateException("가입 코드를 " + MAX_TRIES + "번 뽑았는데 모두 이미 있다");
    }

    private String draw() {
        char[] code = new char[LENGTH];
        for (int i = 0; i < LENGTH; i++) {
            code[i] = ALPHABET.charAt(random.nextInt(ALPHABET.length()));
        }
        return new String(code);
    }
}
