package com.jglearning.jg4seasons.academy;

import static org.assertj.core.api.Assertions.assertThat;

import com.jglearning.jg4seasons.IntegrationTest;
import com.jglearning.jg4seasons.TestData;
import com.jglearning.jg4seasons.academy.api.BookLicenses;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 실제로 커밋되는 사용권 쓰기의 동시성(리뷰 #47-3). 호출자가 트랜잭션에서 먼저 무엇을 읽었든(REPEATABLE READ 스냅숏), 같은 학원·권의 첫 지정이 동시에 두 번
 * 와도 setLicense가 스스로 학원을 잠그고 사용권 줄을 잠금 읽기로 읽어 한 줄로 끝나야 한다. 넣은 행은 끝에 지운다.
 */
@IntegrationTest
class BookLicensesCommitTest {

    static final LocalDate TODAY = LocalDate.of(2026, 12, 10);

    @Autowired
    BookLicenses licenses;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PlatformTransactionManager transactionManager;

    TestData data;
    long bookId;
    List<Long> academyIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        data = new TestData(jdbc);
        bookId = data.bookId("L2-B1");
    }

    @AfterEach
    void cleanUp() {
        for (long academyId : academyIds) {
            jdbc.update("delete from book_license where academy_id = ?", academyId);
            jdbc.update("delete from academy where id = ?", academyId);
        }
    }

    /** 트랜잭션 하나: 먼저 읽어 스냅숏을 잡고 → 둘 다 읽을 때까지 기다린 뒤 → 지정 */
    private Future<?> readThenSet(ExecutorService pool, CyclicBarrier bothRead, long academyId, int seats) {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        return pool.submit(() -> tx.executeWithoutResult(status -> {
            licenses.licensesOf(academyId, TODAY);
            try {
                bothRead.await(10, TimeUnit.SECONDS);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
            licenses.setLicense(academyId, bookId, TODAY.minusDays(9), TODAY.plusDays(80), seats, TODAY);
        }));
    }

    @Test
    void 먼저_읽은_호출자의_동시_첫_지정도_한_줄() throws Exception {
        for (int round = 0; round < 5; round++) {
            long academyId = data.academy("동시 쓰기 학원 " + round);
            academyIds.add(academyId);
            data.enforceLicenses(academyId);
            ExecutorService pool = Executors.newFixedThreadPool(2);
            try {
                CyclicBarrier bothRead = new CyclicBarrier(2);
                Future<?> first = readThenSet(pool, bothRead, academyId, 10);
                Future<?> second = readThenSet(pool, bothRead, academyId, 20);

                String as = "회차 " + round;
                first.get(30, TimeUnit.SECONDS);
                second.get(30, TimeUnit.SECONDS);
                assertThat(jdbc.queryForObject(
                                "select count(*) from book_license where academy_id = ?", Integer.class, academyId))
                        .as(as)
                        .isEqualTo(1);
            } finally {
                pool.shutdownNow();
            }
        }
    }
}
