package com.jglearning.jg4seasons;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    MySQLContainer mysqlContainer() {
        // 운영과 같은 8.x LTS. latest는 9.x가 받아질 수 있다
        return new MySQLContainer(DockerImageName.parse("mysql:8.4"));
    }
}
