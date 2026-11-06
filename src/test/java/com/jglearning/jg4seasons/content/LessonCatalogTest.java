package com.jglearning.jg4seasons.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jglearning.jg4seasons.IntegrationTest;
import com.jglearning.jg4seasons.TestData;
import com.jglearning.jg4seasons.content.api.LessonCatalog;
import com.jglearning.jg4seasons.content.api.LessonContent;
import com.jglearning.jg4seasons.content.api.LessonContent.Passage;
import com.jglearning.jg4seasons.content.api.LessonContent.Speed;
import com.jglearning.jg4seasons.content.api.LessonContent.TimeRange;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** 회차 내용(2.3 재료): 지문·문장·속도별 지문 트랙·문장 시각·레벨·완료 규칙·단어 수. */
@IntegrationTest
@Transactional
class LessonCatalogTest {

    static final String CODE = "L2-B1-05";

    @Autowired
    LessonCatalog catalog;

    @Autowired
    JdbcTemplate jdbc;

    TestData data;

    @BeforeEach
    void setUp() {
        data = new TestData(jdbc);
    }

    @Test
    void 회차_내용을_지문_문장_속도_순서대로() {
        Map<String, Long> tracks = data.lessonContent(CODE, 2, 3, List.of("0.8", "1.0"));

        LessonContent lesson = catalog.findByCode(CODE).orElseThrow();

        assertThat(lesson.code()).isEqualTo(CODE);
        assertThat(lesson.number()).isEqualTo(5);
        assertThat(lesson.title()).isEqualTo("Reading Day 5");
        assertThat(lesson.type()).isEqualTo("regular");
        assertThat(lesson.bookId()).isEqualTo(data.bookId("L2-B1"));
        assertThat(lesson.passages()).extracting(Passage::no).containsExactly(1, 2);
        Passage first = lesson.passages().get(0);
        assertThat(first.title()).isEqualTo("Made-up Passage 1");
        assertThat(first.wordCount()).isEqualTo(101);
        assertThat(first.sentences()).extracting(LessonContent.Sentence::no).containsExactly(1, 2, 3);
        assertThat(first.sentences().get(0).text()).isEqualTo("Passage 1 sentence 1 is a made-up line.");
        assertThat(first.sentences().get(0).speaker()).isNull();
        assertThat(first.sentences().get(1).speaker()).isEqualTo("A");
        assertThat(first.speeds()).extracting(Speed::speed).containsExactly("0.8", "1.0");
        Speed one = first.speeds().get(1);
        assertThat(one.trackId()).isEqualTo(tracks.get("p1:1.0"));
        assertThat(one.fileKey()).isEqualTo("audio/L2-B1-05/L2-B1-05_p1_1.0.mp3");
        assertThat(one.durationMs()).isEqualTo(3 * TestData.SENTENCE_MS + 2 * TestData.GAP_MS);
    }

    @Test
    void 시각은_문장_순서와_같다() {
        data.lessonContent(CODE, 1, 3, List.of("1.0"));

        Speed one = catalog.findByCode(CODE)
                .orElseThrow()
                .passages()
                .get(0)
                .speeds()
                .get(0);

        assertThat(one.timings())
                .containsExactly(new TimeRange(0, 3000), new TimeRange(3500, 6500), new TimeRange(7000, 10000));
        assertThat(one.timingsApproximate()).isFalse();
    }

    @Test
    void 근사_시각이면_timingsApproximate() {
        data.lessonContent(CODE, 1, 2, List.of("0.8", "1.0"));

        Speed slow = catalog.findByCode(CODE)
                .orElseThrow()
                .passages()
                .get(0)
                .speeds()
                .get(0);

        assertThat(slow.speed()).isEqualTo("0.8");
        assertThat(slow.timingsApproximate()).isTrue();
        assertThat(slow.timings().get(0)).isEqualTo(new TimeRange(0, 3750));
    }

    @Test
    void 레벨과_완료_규칙() {
        data.lessonContent(CODE, 1, 1, List.of("1.0"));

        LessonContent lesson = catalog.findByCode(CODE).orElseThrow();

        assertThat(lesson.level()).isEqualTo(new LessonContent.Level("L2", "그린", "#1F7A4F"));
        assertThat(lesson.rule().coverageThreshold()).isEqualByComparingTo("0.900");
        assertThat(lesson.rule().requiredTracksJson()).contains("\"anyOf\"").contains("1.0");
    }

    @Test
    void 없는_회차는_empty() {
        assertThat(catalog.findByCode("L9-B9-99")).isEmpty();
    }

    @Test
    void 지문이_아직_없는_회차는_빈_지문_목록() {
        LessonContent lesson = catalog.findByCode(CODE).orElseThrow();

        assertThat(lesson.passages()).isEmpty();
        assertThat(lesson.wordTotal()).isZero();
    }

    @Test
    void 시각이_없는_트랙은_빈_목록() {
        Map<String, Long> tracks = data.lessonContent(CODE, 1, 2, List.of("1.0"));
        jdbc.update("delete from sentence_timing where track_id = ?", tracks.get("p1:1.0"));

        Speed one = catalog.findByCode(CODE)
                .orElseThrow()
                .passages()
                .get(0)
                .speeds()
                .get(0);

        assertThat(one.timings()).isEmpty();
        assertThat(one.timingsApproximate()).isFalse();
    }

    @Test
    void 시각_개수가_문장_수와_다르면_IllegalState() {
        Map<String, Long> tracks = data.lessonContent(CODE, 1, 3, List.of("1.0"));
        jdbc.update(
                "delete st from sentence_timing st join sentence s on s.id = st.sentence_id"
                        + " where st.track_id = ? and s.no = 2",
                tracks.get("p1:1.0"));

        assertThatThrownBy(() -> catalog.findByCode(CODE)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 단어_수() {
        data.lessonContent(CODE, 1, 1, List.of("1.0"));
        data.words(CODE, 4);

        assertThat(catalog.findByCode(CODE).orElseThrow().wordTotal()).isEqualTo(4);
    }
}
