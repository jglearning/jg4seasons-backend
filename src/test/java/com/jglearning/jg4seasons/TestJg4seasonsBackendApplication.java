package com.jglearning.jg4seasons;

import org.springframework.boot.SpringApplication;

public class TestJg4seasonsBackendApplication {

    public static void main(String[] args) {
        SpringApplication.from(Jg4seasonsBackendApplication::main)
                .with(TestcontainersConfiguration.class)
                .run(args);
    }
}
