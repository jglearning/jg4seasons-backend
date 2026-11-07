package com.jglearning.jg4seasons.infra;

import static org.assertj.core.api.Assertions.assertThat;

import com.jglearning.jg4seasons.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/** 배포 스크립트·Nginx(/api/health)가 보는 readiness는 로그인 없이 열리고, 그 밖의 actuator는 열리지 않는다. */
@IntegrationTest
class HealthEndpointTest {

    @Autowired
    MockMvcTester mvc;

    @Test
    void readiness는_로그인_없이_UP() {
        var result = mvc.get().uri("/actuator/health/readiness").exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.status").isEqualTo("UP");
    }

    @Test
    void liveness도_로그인_없이_UP() {
        assertThat(mvc.get().uri("/actuator/health/liveness").exchange()).hasStatusOk();
    }

    @Test
    void health_밖의_actuator는_열리지_않는다() {
        assertThat(mvc.get().uri("/actuator/info").exchange()).hasStatus(401);
        assertThat(mvc.get().uri("/actuator/env").exchange()).hasStatus(401);
    }
}
