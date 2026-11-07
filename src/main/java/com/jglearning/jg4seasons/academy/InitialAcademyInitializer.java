package com.jglearning.jg4seasons.academy;

import com.jglearning.jg4seasons.auth.api.NewStaff;
import com.jglearning.jg4seasons.auth.api.StaffAccount;
import com.jglearning.jg4seasons.auth.api.StaffAccounts;
import com.jglearning.jg4seasons.auth.api.StaffRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 학원이 하나도 없으면 설정값(jg4seasons.initial)으로 첫 학원과 관리자를 만든다(SYS-01). 관리자 계정은 로그인한 관리자만 만들 수 있어서 맨 처음 한
 * 명은 로그인 없이 만들 방법이 필요하다. 운영 비밀번호를 저장소(설정 파일·마이그레이션)에 두지 않으려고 실행 환경의 설정값으로 받는다.
 *
 * <p>"학원이 하나도 없음"으로 판단한다(학원 범위 데이터를 학원을 가로질러 조회하지 않는다). 시험 시드(DevSeedRunner, @Order(0))보다 먼저 돌고
 * 학원과 관리자를 한 트랜잭션에서 만들므로, 학원이 있으면 이미 만들어진 것이다 — 서버를 다시 띄워도 또 만들거나 비밀번호를 바꾸지 않는다. 학원이 있으면
 * 설정값을 읽지 않아 검사도 하지 않는다.
 */
@Component
@Order(-10)
class InitialAcademyInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(InitialAcademyInitializer.class);

    static final String SETTINGS = "{\"timezone\":\"Asia/Seoul\"}";

    private final AcademyRepository academies;
    private final StaffAccounts staffAccounts;
    private final InitialAcademyProperties properties;

    InitialAcademyInitializer(
            AcademyRepository academies, StaffAccounts staffAccounts, InitialAcademyProperties properties) {
        this.academies = academies;
        this.staffAccounts = staffAccounts;
        this.properties = properties;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        createIfNoAcademy();
    }

    void createIfNoAcademy() {
        if (academies.count() > 0) {
            log.debug("학원이 이미 있어 첫 학원을 만들지 않는다");
            return;
        }
        properties
                .resolve()
                .ifPresentOrElse(
                        this::create,
                        () -> log.warn("학원이 하나도 없는데 첫 학원 설정({})이 없어 만들지 않았다", InitialAcademyProperties.PREFIX));
    }

    void create(InitialAcademy initial) {
        Academy academy = academies.save(Academy.create(initial.academyName(), initial.academySlug(), SETTINGS));
        StaffAccount admin = staffAccounts.create(
                academy.getId(),
                new NewStaff(initial.adminName(), initial.adminLoginId(), initial.adminPassword(), StaffRole.ADMIN));
        // 값(이름·아이디·비밀번호)은 남기지 않는다
        log.info("첫 학원과 관리자를 만들었다: academyId={}, adminId={}", academy.getId(), admin.id());
    }
}
