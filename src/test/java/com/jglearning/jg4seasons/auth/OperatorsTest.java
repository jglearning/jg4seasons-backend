package com.jglearning.jg4seasons.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jglearning.jg4seasons.IntegrationTest;
import com.jglearning.jg4seasons.TestData;
import com.jglearning.jg4seasons.auth.api.Operators;
import com.jglearning.jg4seasons.infra.web.error.ApiException;
import com.jglearning.jg4seasons.infra.web.error.CommonErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** 시스템 API가 쓰기·읽기 전에 부르는 "지금도 활성 운영자인가"(남은 access 30분 대비, ADR 0032). 아니면 401. */
@IntegrationTest
@Transactional
class OperatorsTest {

    @Autowired
    Operators operators;

    @Autowired
    JdbcTemplate jdbc;

    private static void assertUnauthorized(Runnable call) {
        assertThatThrownBy(call::run)
                .isInstanceOfSatisfying(
                        ApiException.class, e -> assertThat(e.errorCode()).isEqualTo(CommonErrorCode.UNAUTHORIZED));
    }

    @Test
    void 활성_운영자는_통과한다() {
        long operatorId = new TestData(jdbc).operator("운영자");

        assertThatCode(() -> operators.requireActiveOperator(operatorId)).doesNotThrowAnyException();
    }

    @Test
    void 비활성_운영자는_401() {
        long operatorId = new TestData(jdbc).operator("운영자");
        jdbc.update("update `user` set status = 'inactive' where id = ?", operatorId);

        assertUnauthorized(() -> operators.requireActiveOperator(operatorId));
    }

    @Test
    void 학원_관리자와_없는_사용자는_401() {
        TestData data = new TestData(jdbc);
        long adminId = data.staff(data.academy("관리 학원"), "관리자", "admin");

        assertUnauthorized(() -> operators.requireActiveOperator(adminId));
        assertUnauthorized(() -> operators.requireActiveOperator(Long.MAX_VALUE));
    }
}
