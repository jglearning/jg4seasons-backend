package com.jglearning.jg4seasons.auth;

import com.jglearning.jg4seasons.auth.persistence.PreAuthUserLookup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 운영자가 한 명도 없으면 설정값(jg4seasons.initial-operator)으로 운영자 계정을 만든다(ADR 0032). 운영자는 학원에 속하지 않아 관리자 화면으로 만들 수
 * 없고, 비밀번호를 저장소에 두지 않으려고 실행 환경의 설정값으로 받는다.
 *
 * <p>운영자가 있으면 설정값을 읽지 않는다 — 서버를 다시 띄워도 또 만들거나 비밀번호를 바꾸지 않는다. 값이 없으면 WARN만 남기고 서버는 뜬다(첫 학원과 달리
 * 운영자가 없어도 학원들은 돈다). 첫 학원 부트스트랩(@Order(-10)) 다음에 돈다.
 */
@Component
@Order(-9)
class InitialOperatorInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(InitialOperatorInitializer.class);

    private final PreAuthUserLookup users;
    private final StaffAccountService accounts;
    private final InitialOperatorProperties properties;

    InitialOperatorInitializer(
            PreAuthUserLookup users, StaffAccountService accounts, InitialOperatorProperties properties) {
        this.users = users;
        this.accounts = accounts;
        this.properties = properties;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        createIfNoOperator();
    }

    void createIfNoOperator() {
        if (users.anyOperator()) {
            log.debug("운영자가 이미 있어 만들지 않는다");
            return;
        }
        properties
                .resolve()
                .ifPresentOrElse(
                        this::create,
                        () -> log.warn("운영자가 한 명도 없는데 운영자 설정({})이 없어 만들지 않았다", InitialOperatorProperties.PREFIX));
    }

    void create(InitialOperator operator) {
        long userId = accounts.createOperator(operator.loginId(), operator.password(), operator.name());
        // 값(아이디·비밀번호)은 남기지 않는다
        log.info("운영자 계정을 만들었다: userId={}", userId);
    }
}
