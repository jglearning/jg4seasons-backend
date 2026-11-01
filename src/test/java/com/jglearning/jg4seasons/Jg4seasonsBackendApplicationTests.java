package com.jglearning.jg4seasons;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class Jg4seasonsBackendApplicationTests {

	@Test
	void contextLoads() {
	}

}
