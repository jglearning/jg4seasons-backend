package com.jglearning.jg4seasons.auth.devseed;

import static org.assertj.core.api.Assertions.assertThat;

import com.jglearning.jg4seasons.IntegrationTest;
import com.jglearning.jg4seasons.academy.api.AcademyDirectory;
import com.jglearning.jg4seasons.auth.AuthIdentityRepository;
import com.jglearning.jg4seasons.auth.AuthProvider;
import com.jglearning.jg4seasons.auth.PersonalCodeHasher;
import com.jglearning.jg4seasons.auth.UserRepository;
import com.jglearning.jg4seasons.infra.persistence.devseed.DevSeed;
import java.time.Clock;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.transaction.annotation.Transactional;

/** local 프로파일 개발 시드: 데모 학생01~14에 개인 코드 1001~1014(DB에는 HMAC만). */
@IntegrationTest
@Transactional
class AuthDevSeedTest {

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
    PersonalCodeHasher hasher;

    @Autowired
    ApplicationContext context;

    @Autowired
    MockMvcTester mvc;

    private AuthDevSeed authDevSeed() {
        return new AuthDevSeed(academies, users, identities, hasher, clock);
    }

    private long demoAcademyId() {
        new DevSeed(jdbc, clock).seed();
        return jdbc.queryForObject("select id from academy where slug = 'demo'", Long.class);
    }

    @Test
    void 데모_학생에게_1001부터_개인_코드를_넣는다() {
        long demo = demoAcademyId();

        assertThat(authDevSeed().seed()).isEqualTo(14);

        List<Long> students = jdbc.queryForList(
                "select id from `user` where academy_id = ? and role = 'student' order by id", Long.class, demo);
        assertThat(identities.findByProviderAndIdentifier(AuthProvider.PERSONAL_CODE, hasher.hash(demo, "1001")))
                .hasValueSatisfying(identity -> assertThat(identity.getUserId()).isEqualTo(students.get(0)));
        assertThat(identities.findByProviderAndIdentifier(AuthProvider.PERSONAL_CODE, hasher.hash(demo, "1014")))
                .hasValueSatisfying(identity -> assertThat(identity.getUserId()).isEqualTo(students.get(13)));
        assertThat(jdbc.queryForObject(
                        "select count(*) from auth_identity where identifier in ('1001', '1014')", Integer.class))
                .as("평문 없음")
                .isZero();
    }

    @Test
    void 다시_돌려도_중복으로_넣지_않는다() {
        demoAcademyId();
        authDevSeed().seed();

        assertThat(authDevSeed().seed()).isZero();
    }

    @Test
    void 데모_학원이_없으면_아무것도_안_한다() {
        assertThat(authDevSeed().seed()).isZero();
    }

    @Test
    void 시드한_코드로_데모_반에_입장한다() {
        demoAcademyId();
        authDevSeed().seed();

        assertThat(mvc.post()
                        .uri("/api/v1/auth/student/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"G7KM2XP4\",\"personalCode\":\"1001\"}")
                        .with(request -> {
                            request.setRemoteAddr("192.0.2.200");
                            return request;
                        }))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.user.name")
                .isEqualTo("학생01");
    }

    @Test
    void test_프로파일에는_시드_러너가_없다() {
        assertThat(context.getBeansOfType(AuthDevSeedRunner.class)).isEmpty();
    }
}
