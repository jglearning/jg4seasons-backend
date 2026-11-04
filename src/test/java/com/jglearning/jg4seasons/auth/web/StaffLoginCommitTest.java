package com.jglearning.jg4seasons.auth.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.jglearning.jg4seasons.IntegrationTest;
import com.jglearning.jg4seasons.MutableClock;
import com.jglearning.jg4seasons.TestData;
import com.jglearning.jg4seasons.auth.AuthIdentity;
import com.jglearning.jg4seasons.auth.AuthIdentityRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/**
 * 실제로 커밋되는 요청에서의 계정 잠금. 트랜잭션으로 되돌리는 테스트에서는 서비스가 테스트 트랜잭션에 합류해, 401 예외로 롤백되어 실패 횟수가 사라지는
 * 버그를 보지 못한다. 넣은 데이터는 끝에 지운다.
 */
@IntegrationTest
class StaffLoginCommitTest {

    static final String PASSWORD = "teacher-pass-1";

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PasswordEncoder encoder;

    @Autowired
    AuthIdentityRepository identities;

    @Autowired
    MutableClock clock;

    long academyId;
    long teacherId;
    String loginId;

    @BeforeEach
    void setUp() {
        clock.reset();
        TestData data = new TestData(jdbc);
        academyId = data.academy("커밋 학원");
        teacherId = data.staff(academyId, "교사", "teacher");
        loginId = "commit" + teacherId;
        identities.save(AuthIdentity.password(teacherId, loginId, encoder.encode(PASSWORD), clock.instant()));
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("delete from refresh_token where user_id = ?", teacherId);
        jdbc.update("delete from auth_identity where user_id = ?", teacherId);
        jdbc.update("delete from `user` where id = ?", teacherId);
        jdbc.update("delete from academy where id = ?", academyId);
    }

    private int login(String password, String ip) {
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

    private int failedAttempts() {
        return jdbc.queryForObject(
                "select failed_attempts from auth_identity where user_id = ?", Integer.class, teacherId);
    }

    @Test
    void 틀린_비밀번호의_실패_횟수는_요청이_끝나도_DB에_남는다() {
        assertThat(login("wrong-pass-1", "198.18.9.1")).isEqualTo(401);

        assertThat(failedAttempts()).as("401 예외로 롤백되면 0").isEqualTo(1);
    }

    @Test
    void 같은_계정_동시_실패도_5번째에서_잠긴다() throws Exception {
        int requests = 10;
        ExecutorService pool = Executors.newFixedThreadPool(requests);
        CountDownLatch ready = new CountDownLatch(requests);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();
        for (int i = 0; i < requests; i++) {
            String ip = "198.18.8." + i;
            futures.add(pool.submit(() -> {
                ready.countDown();
                go.await();
                return login("wrong-pass-1", ip);
            }));
        }
        ready.await();
        go.countDown();
        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> future : futures) {
            statuses.add(future.get(30, TimeUnit.SECONDS));
        }
        pool.shutdown();

        assertThat(statuses).filteredOn(s -> s == 401).as("확인까지 간 틀린 시도").hasSize(4);
        assertThat(statuses).filteredOn(s -> s == 423).hasSize(6);
        assertThat(failedAttempts()).isEqualTo(5);
    }
}
