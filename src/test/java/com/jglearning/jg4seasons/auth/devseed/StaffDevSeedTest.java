package com.jglearning.jg4seasons.auth.devseed;

import static org.assertj.core.api.Assertions.assertThat;

import com.jglearning.jg4seasons.IntegrationTest;
import com.jglearning.jg4seasons.academy.api.AcademyDirectory;
import com.jglearning.jg4seasons.auth.AuthIdentityRepository;
import com.jglearning.jg4seasons.auth.UserRepository;
import com.jglearning.jg4seasons.auth.api.StaffAccounts;
import com.jglearning.jg4seasons.clazz.api.ClassDirectory;
import com.jglearning.jg4seasons.clazz.api.ClassTeachers;
import com.jglearning.jg4seasons.infra.persistence.devseed.DevSeed;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.transaction.annotation.Transactional;

/** local 개발 시드: 데모 학원 관리자 `admin`, 교사 `teacher01`(데모 반 담당). DB에는 BCrypt 해시만. */
@IntegrationTest
@Transactional
class StaffDevSeedTest {

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    Clock clock;

    @Autowired
    AcademyDirectory academies;

    @Autowired
    UserRepository users;

    @Autowired
    AuthIdentityRepository identities;

    @Autowired
    PasswordEncoder encoder;

    @Autowired
    StaffAccounts accounts;

    @Autowired
    ClassDirectory classes;

    @Autowired
    ClassTeachers classTeachers;

    @Autowired
    MockMvcTester mvc;

    private StaffDevSeed staffDevSeed() {
        return new StaffDevSeed(academies, users, identities, encoder, accounts, classes, classTeachers, clock);
    }

    private long demoAcademyId() {
        new DevSeed(jdbc, clock).seed();
        return jdbc.queryForObject("select id from academy where slug = 'demo'", Long.class);
    }

    private int login(String loginId, String password, String ip) {
        return mvc.post()
                .uri("/api/v1/auth/staff/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"loginId\":\"" + loginId + "\",\"password\":\"" + password + "\"}")
                .with(request -> {
                    request.setRemoteAddr(ip);
                    return request;
                })
                .exchange()
                .getResponse()
                .getStatus();
    }

    @Test
    void 관리자와_교사_로그인_수단을_넣고_교사는_데모_반을_맡는다() {
        long demo = demoAcademyId();

        assertThat(staffDevSeed().seed()).isEqualTo(2);

        assertThat(login("admin", StaffDevSeed.ADMIN_PASSWORD, "192.0.2.210")).isEqualTo(200);
        assertThat(login("teacher01", StaffDevSeed.TEACHER_PASSWORD, "192.0.2.211"))
                .isEqualTo(200);
        long teacherId = accounts.list(demo).stream()
                .filter(account -> "teacher01".equals(account.loginId()))
                .findFirst()
                .orElseThrow()
                .id();
        long demoClass =
                classes.findActiveByAccessCode("G7KM2XP4").orElseThrow().id();
        assertThat(classTeachers.classIdsOf(demo, teacherId)).containsExactly(demoClass);
        assertThat(jdbc.queryForObject(
                        "select count(*) from auth_identity where secret_hash = ?",
                        Integer.class,
                        StaffDevSeed.TEACHER_PASSWORD))
                .as("평문 없음")
                .isZero();
    }

    @Test
    void 다시_돌려도_중복으로_넣지_않는다() {
        demoAcademyId();
        staffDevSeed().seed();

        assertThat(staffDevSeed().seed()).isZero();
    }

    @Test
    void 데모_학원이_없으면_아무것도_안_한다() {
        assertThat(staffDevSeed().seed()).isZero();
    }
}
