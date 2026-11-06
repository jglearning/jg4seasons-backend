package com.jglearning.jg4seasons.infra.persistence.devseed;

import static org.assertj.core.api.Assertions.assertThat;

import com.jglearning.jg4seasons.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** 로컬 개발용 회차 내용(L2-B1-01~24): 지어낸 문장 + 더미 음원 경로 + 문장 시각. 교재 지문은 저장소에 넣지 않는다. */
@IntegrationTest
@Transactional
class ContentDevSeedTest {

    @Autowired
    JdbcTemplate jdbc;

    private int count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    @Test
    void 회차마다_지문_6_문장_3_속도_3() {
        assertThat(new ContentDevSeed(jdbc).seed()).isTrue();

        assertThat(
                        count(
                                "select count(*) from passage p join lesson l on l.id = p.lesson_id where l.code like 'L2-B1-%'"))
                .isEqualTo(24 * 6);
        assertThat(count("select count(*) from sentence s join passage p on p.id = s.passage_id"
                        + " join lesson l on l.id = p.lesson_id where l.code = 'L2-B1-05'"))
                .isEqualTo(6 * 3);
        assertThat(count("select count(*) from track t join lesson l on l.id = t.lesson_id"
                        + " where l.code = 'L2-B1-05' and t.kind = 'passage'"))
                .as("L2 속도 0.8·1.0·1.2")
                .isEqualTo(6 * 3);
        assertThat(count("select count(*) from track where file_key = 'audio/L2-B1-05/L2-B1-05_p1_1.0.mp3'"
                        + " and variant = 'p1:1.0' and duration_ms = 10000"))
                .isEqualTo(1);
        assertThat(count("select count(*) from sentence_timing st join track t on t.id = st.track_id"
                        + " join lesson l on l.id = t.lesson_id where l.code = 'L2-B1-05'"))
                .isEqualTo(6 * 3 * 3);
    }

    @Test
    void 두_번_넣어도_한_번() {
        assertThat(new ContentDevSeed(jdbc).seed()).isTrue();
        assertThat(new ContentDevSeed(jdbc).seed()).isFalse();

        assertThat(count(
                        "select count(*) from passage p join lesson l on l.id = p.lesson_id where l.code = 'L2-B1-01'"))
                .isEqualTo(6);
    }

    @Test
    void 속도_1_0의_시각은_정확_나머지는_근사() {
        new ContentDevSeed(jdbc).seed();

        assertThat(count("select count(*) from sentence_timing st join track t on t.id = st.track_id"
                        + " join sentence s on s.id = st.sentence_id"
                        + " where t.file_key = 'audio/L2-B1-05/L2-B1-05_p1_1.0.mp3' and s.no = 2"
                        + " and st.start_ms = 3500 and st.end_ms = 6500 and st.approximate = false"))
                .isEqualTo(1);
        assertThat(count("select count(*) from sentence_timing st join track t on t.id = st.track_id"
                        + " where t.variant like '%:0.8' or t.variant like '%:1.2'"))
                .isEqualTo(count("select count(*) from sentence_timing st join track t on t.id = st.track_id"
                        + " where (t.variant like '%:0.8' or t.variant like '%:1.2') and st.approximate = true"));
    }
}
