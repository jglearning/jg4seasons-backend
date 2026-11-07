package com.jglearning.jg4seasons.infra.persistence.devseed;

import static org.assertj.core.api.Assertions.assertThat;

import com.jglearning.jg4seasons.IntegrationTest;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** local 프로파일 개발용 시드. 테스트마다 트랜잭션을 되돌려 다른 테스트에 데이터가 남지 않는다. */
@IntegrationTest
@Transactional
class DevSeedTest {

    /** UTC 11/3 16:00 = 서울 11/4 01:00. 날짜 경계를 걸친 시각 */
    static final Clock AFTER_SEOUL_MIDNIGHT = Clock.fixed(Instant.parse("2026-11-03T16:00:00Z"), ZoneOffset.UTC);

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ApplicationContext context;

    private DevSeed devSeed() {
        return new DevSeed(jdbc, AFTER_SEOUL_MIDNIGHT);
    }

    private int count(String sql) {
        return jdbc.queryForObject(sql, Integer.class);
    }

    @Test
    void 시드는_학원_반_학생_진도표를_만든다() {
        assertThat(devSeed().seed()).isTrue();

        assertThat(count("select count(*) from academy where slug = 'demo'")).isEqualTo(1);
        assertThat(count("select count(*) from `user` u join academy a on a.id = u.academy_id"
                        + " where a.slug = 'demo' and u.role = 'student'"))
                .isEqualTo(14);
        assertThat(count("select count(*) from `user` u join academy a on a.id = u.academy_id"
                        + " where a.slug = 'demo' and u.role = 'admin'"))
                .isEqualTo(1);
        assertThat(count("select count(*) from enrollment e join `class` c on c.id = e.class_id"
                        + " where c.access_code = 'G7KM2XP4' and e.left_at is null"))
                .isEqualTo(14);
        assertThat(count("select count(*) from class_teacher ct join `class` c on c.id = ct.class_id"
                        + " where c.access_code = 'G7KM2XP4'"))
                .isEqualTo(1);
        assertThat(count("select count(*) from class_session s join `class` c on c.id = s.class_id"
                        + " where c.access_code = 'G7KM2XP4'"))
                .isEqualTo(24);
        assertThat(jdbc.queryForObject(
                        "select l.code from class_session s join lesson l on l.id = s.lesson_id"
                                + " join `class` c on c.id = s.class_id"
                                + " where c.access_code = 'G7KM2XP4' and s.seq = 1",
                        String.class))
                .isEqualTo("L2-B1-01");
        // 인증 수단은 인증 이슈에서 시드에 추가한다
        assertThat(count("select count(*) from auth_identity")).isZero();
    }

    @Test
    void 시드_기준일은_서울_날짜다() {
        devSeed().seed();

        // 서울 11/4 − 14일. UTC 날짜(11/3)로 계산하면 10/20이 된다
        assertThat(jdbc.queryForObject(
                        "select start_date from `class` where access_code = 'G7KM2XP4'", LocalDate.class))
                .isEqualTo(LocalDate.of(2026, 10, 21));
    }

    @Test
    void 진도표는_화목만이고_날짜순이다() {
        devSeed().seed();

        LocalDate start =
                jdbc.queryForObject("select start_date from `class` where access_code = 'G7KM2XP4'", LocalDate.class);
        List<LocalDate> dates = jdbc.queryForList(
                "select s.scheduled_date from class_session s join `class` c on c.id = s.class_id"
                        + " where c.access_code = 'G7KM2XP4' order by s.seq",
                LocalDate.class);

        assertThat(dates).hasSize(24).isSorted().doesNotHaveDuplicates();
        assertThat(dates).allSatisfy(d -> {
            assertThat(d.getDayOfWeek()).isIn(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY);
            assertThat(d).isAfterOrEqualTo(start);
        });
        // 기준일(11/4) 전에 수업이 있어야 "오늘 회차"·밀린 수업을 로컬에서 볼 수 있다
        assertThat(dates.get(0)).isBefore(LocalDate.of(2026, 11, 4));
    }

    // 운영 시험(trial)은 Parameter Store의 가입 코드로 반을 만든다
    @Test
    void 넘긴_가입_코드로_반을_만든다() {
        assertThat(new DevSeed(jdbc, AFTER_SEOUL_MIDNIGHT, "ZQ7W2XM9").seed()).isTrue();

        assertThat(count("select count(*) from `class` c join academy a on a.id = c.academy_id"
                        + " where a.slug = 'demo' and c.access_code = 'ZQ7W2XM9'"))
                .isEqualTo(1);
        assertThat(count("select count(*) from `class` where access_code = 'G7KM2XP4'"))
                .isZero();
    }

    @Test
    void 시드를_두_번_돌려도_한_벌이다() {
        assertThat(devSeed().seed()).isTrue();
        assertThat(devSeed().seed()).isFalse();

        assertThat(count("select count(*) from academy where slug = 'demo'")).isEqualTo(1);
        assertThat(count("select count(*) from `class` where access_code = 'G7KM2XP4'"))
                .isEqualTo(1);
    }

    @Test
    void 개발_시드_러너는_local_프로파일에서만_뜬다() {
        assertThat(context.getBeansOfType(DevSeedRunner.class)).isEmpty();
    }
}
