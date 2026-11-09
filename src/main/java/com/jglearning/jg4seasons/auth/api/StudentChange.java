package com.jglearning.jg4seasons.auth.api;

import java.util.Optional;

/** 학생 변경. 빈 Optional은 그대로. school·grade는 Optional.of("")(공백만 포함)이면 지운다. */
public record StudentChange(
        Optional<String> name, Optional<String> school, Optional<String> grade, Optional<AccountStatus> status) {}
