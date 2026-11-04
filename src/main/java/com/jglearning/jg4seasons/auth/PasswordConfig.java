package com.jglearning.jg4seasons.auth;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/** 교사·관리자 비밀번호 해시: BCrypt 비용 10. */
@Configuration(proxyBeanMethods = false)
public class PasswordConfig {

    static final int BCRYPT_STRENGTH = 10;

    /**
     * 없는 로그인 아이디도 이 값과 비교해 응답 시간을 맞춘다(아이디 존재 노출 방지). 기동 때 한 번 만든 무작위 비밀번호의 해시라 어떤 입력과도 맞지
     * 않는다
     */
    static final String DUMMY_HASH = new BCryptPasswordEncoder(BCRYPT_STRENGTH)
            .encode(java.util.UUID.randomUUID().toString());

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(BCRYPT_STRENGTH);
    }
}
