package com.jglearning.jg4seasons.auth.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.jglearning.jg4seasons.IntegrationTest;
import com.jglearning.jg4seasons.TestData;
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
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 학생 설정의 처음 저장이 동시에 올 때(폰과 태블릿에서 같은 순간에 처음 목소리를 고른다). 실제로 커밋되는 트랜잭션끼리의 일이라 롤백 테스트가 아니다 — 만든 행은 직접 지운다.
 */
@IntegrationTest
class UserSettingCommitTest {

    @Autowired
    UserSettingStore store;

    @Autowired
    JdbcTemplate jdbc;

    TestData data;
    long academyId;

    @BeforeEach
    void setUp() {
        data = new TestData(jdbc);
        academyId = data.academy("설정 커밋 학원");
    }

    @AfterEach
    void cleanUp() {
        jdbc.update(
                "delete from user_setting where user_id in"
                        + " (select id from (select id from `user` where academy_id = ?) u)",
                academyId);
        jdbc.update("delete from `user` where academy_id = ?", academyId);
        jdbc.update("delete from academy where id = ?", academyId);
    }

    @Test
    void 처음_저장_동시_2건은_오류_없이_행_하나만_만든다() throws Exception {
        // 죽여야 하는 틀린 구현: "조회해서 없으면 insert, 있으면 update" — 둘 다 없다고 보고 insert해 한쪽이 중복 키 오류
        for (int round = 0; round < 20; round++) {
            long studentId = data.student(academyId, "학생" + round);
            ExecutorService pool = Executors.newFixedThreadPool(2);
            CountDownLatch start = new CountDownLatch(1);
            try {
                List<Future<?>> saves = new ArrayList<>();
                for (String voice : List.of("us_m", "uk_w")) {
                    saves.add(pool.submit(() -> {
                        start.await();
                        store.saveVoice(academyId, studentId, voice);
                        return null;
                    }));
                }
                start.countDown();
                for (Future<?> save : saves) {
                    save.get(30, TimeUnit.SECONDS); // 저장에서 난 예외는 여기서 다시 던져진다
                }
            } finally {
                pool.shutdownNow();
            }

            assertThat(jdbc.queryForObject(
                            "select count(*) from user_setting where user_id = ?", Integer.class, studentId))
                    .as("round %d", round)
                    .isEqualTo(1);
            assertThat(store.find(academyId, studentId).orElseThrow().voice())
                    .as("round %d — 나중에 쓴 값이 이긴다(둘 중 하나)", round)
                    .isIn("us_m", "uk_w");
        }
    }
}
