package com.jglearning.jg4seasons;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@IntegrationTest
class Jg4seasonsBackendApplicationTests {

    @Autowired
    Clock clock;

    @Test
    void contextLoads() {}

    @Test
    void Clock_빈은_UTC다() {
        assertThat(clock.getZone()).isEqualTo(ZoneOffset.UTC);
    }
}
