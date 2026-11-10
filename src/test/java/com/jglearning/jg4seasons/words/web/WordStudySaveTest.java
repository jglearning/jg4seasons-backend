package com.jglearning.jg4seasons.words.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.jglearning.jg4seasons.IntegrationTest;
import com.jglearning.jg4seasons.MutableClock;
import com.jglearning.jg4seasons.TestData;
import com.jglearning.jg4seasons.auth.AuthCookieSupport;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.annotation.Transactional;

/**
 * 2.5 PUT /student/lessons/{lessonCode}/word-study — 카드·학습 결과(단조 증가). 회차 L2-B1-05(오늘)는 단어 10개, L2-B1-06은 내일. 동시 저장은
 * WordStudyCommitTest.
 */
@IntegrationTest
@Transactional
class WordStudySaveTest {

    static final String TODAY_CODE = "L2-B1-05";
    static final String TOMORROW_CODE = "L2-B1-06";
    static final int WORDS = 10;
    /** 서울 2026-11-10 12:00 */
    static final Instant NOON_NOV_10 = Instant.parse("2026-11-10T03:00:00Z");

    @Autowired
    MockMvcTester mvc;

    @Autowired
    AuthCookieSupport cookies;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    MutableClock clock;

    TestData data;
    long academyId;
    long classId;
    long studentId;

    @BeforeEach
    void setUp() {
        clock.set(NOON_NOV_10);
        data = new TestData(jdbc);
        academyId = data.academy("단어 학습 학원");
        classId = data.clazz(academyId, "L2-B1", "겨울 그린 A반", TestData.uniqueAccessCode(), "active");
        studentId = data.student(academyId, "김학생");
        data.enroll(classId, studentId);
        data.session(classId, 5, TODAY_CODE, LocalDate.of(2026, 11, 10), "scheduled");
        data.session(classId, 6, TOMORROW_CODE, LocalDate.of(2026, 11, 11), "scheduled");
        for (int i = 1; i <= WORDS; i++) {
            data.word(TODAY_CODE, i, "made-up" + i, "book", "n", false);
        }
    }

    @AfterEach
    void resetClock() {
        clock.reset();
    }

    private MvcTestResult save(String code, String body, Cookie access) {
        return mvc.put()
                .uri("/api/v1/student/lessons/" + code + "/word-study")
                .cookie(access, AuthCookieSupport.csrf())
                .header(AuthCookieSupport.CSRF_HEADER, AuthCookieSupport.CSRF_VALUE)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();
    }

    private MvcTestResult save(String body) {
        return save(TODAY_CODE, body, cookies.studentAccess(studentId, academyId));
    }

    private Map<String, Object> row() {
        return jdbc.queryForMap(
                "select cards_viewed, mem_known, recall_best, spell_best, all_viewed_at, last_viewed_at from word_study"
                        + " where student_id = ? and lesson_id = ?",
                studentId,
                data.lessonId(TODAY_CODE));
    }

    @Test
    void 처음_저장은_보낸_칸만_나머지는_0() {
        MvcTestResult result = save("{\"memKnown\":4}");

        assertThat(result).hasStatusOk();
        var json = assertThat(result).bodyJson();
        json.extractingPath("$.cardsViewed").isEqualTo(0);
        json.extractingPath("$.memKnown").isEqualTo(4);
        json.extractingPath("$.recallBest").isEqualTo(0);
        json.extractingPath("$.spellBest").isEqualTo(0);
        json.extractingPath("$.allViewed").isEqualTo(false);
        assertThat(row())
                .containsEntry("all_viewed_at", null)
                .containsEntry("last_viewed_at", LocalDateTime.of(2026, 11, 10, 3, 0));
    }

    @Test
    void 작은_값은_무시하고_큰_값만_올린다() {
        // 마지막 요청이 가장 작은 값이어야 "그대로 덮어쓰기"를 잡는다
        save("{\"memKnown\":4,\"recallCorrect\":7,\"spellCorrect\":5,\"cardsViewed\":8}");
        save("{\"memKnown\":6,\"recallCorrect\":9,\"spellCorrect\":6,\"cardsViewed\":9}");
        MvcTestResult result = save("{\"memKnown\":2,\"recallCorrect\":3,\"spellCorrect\":1,\"cardsViewed\":4}");

        var json = assertThat(result).bodyJson();
        json.extractingPath("$.memKnown").isEqualTo(6);
        json.extractingPath("$.recallBest").isEqualTo(9);
        json.extractingPath("$.spellBest").isEqualTo(6);
        json.extractingPath("$.cardsViewed").isEqualTo(9);
        assertThat(row())
                .containsEntry("mem_known", 6)
                .containsEntry("recall_best", 9)
                .containsEntry("spell_best", 6)
                .containsEntry("cards_viewed", 9);
    }

    @Test
    void 보내지_않은_칸은_그대로() {
        save("{\"cardsViewed\":5,\"memKnown\":3,\"recallCorrect\":4,\"spellCorrect\":2}");

        MvcTestResult result = save("{\"spellCorrect\":3}");

        var json = assertThat(result).bodyJson();
        json.extractingPath("$.cardsViewed").isEqualTo(5);
        json.extractingPath("$.memKnown").isEqualTo(3);
        json.extractingPath("$.recallBest").isEqualTo(4);
        json.extractingPath("$.spellBest").isEqualTo(3);
    }

    @Test
    void 카드를_다_보면_allViewed이고_처음_시각을_지킨다() {
        assertThat(save("{\"cardsViewed\":9}"))
                .bodyJson()
                .extractingPath("$.allViewed")
                .isEqualTo(false);

        assertThat(save("{\"cardsViewed\":10}"))
                .bodyJson()
                .extractingPath("$.allViewed")
                .isEqualTo(true);
        clock.set(NOON_NOV_10.plusSeconds(3600));
        MvcTestResult later = save("{\"memKnown\":1}");

        assertThat(later).bodyJson().extractingPath("$.allViewed").isEqualTo(true);
        assertThat(row())
                .as("다 본 시각은 처음 한 번만, 마지막 시각은 매번")
                .containsEntry("all_viewed_at", LocalDateTime.of(2026, 11, 10, 3, 0))
                .containsEntry("last_viewed_at", LocalDateTime.of(2026, 11, 10, 4, 0));
    }

    @Test
    void 처음_저장에서_다_봤으면_바로_allViewed() {
        assertThat(save("{\"cardsViewed\":10}"))
                .bodyJson()
                .extractingPath("$.allViewed")
                .isEqualTo(true);
        assertThat(row()).containsEntry("all_viewed_at", LocalDateTime.of(2026, 11, 10, 3, 0));
    }

    @Test
    void 회차_단어_수보다_크면_400() {
        MvcTestResult result = save("{\"spellCorrect\":11}");

        assertThat(result).hasStatus(400);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("VALIDATION");
        assertThat(result).bodyJson().extractingPath("$.errors[0].field").isEqualTo("spellCorrect");
        assertThat(jdbc.queryForObject(
                        "select count(*) from word_study where student_id = ?", Integer.class, studentId))
                .as("거부하면 행을 만들지 않는다")
                .isZero();
    }

    @Test
    void 단어가_없는_회차는_0만_받고_다_본_것으로_치지_않는다() {
        long second = data.clazz(academyId, "L2-B1", "토요 반", TestData.uniqueAccessCode(), "active");
        data.enroll(second, studentId);
        data.session(second, 7, "L2-B1-07", LocalDate.of(2026, 11, 9), "scheduled");
        Cookie access = cookies.studentAccess(studentId, academyId);

        assertThat(save("L2-B1-07", "{\"cardsViewed\":1}", access)).hasStatus(400);
        MvcTestResult zero = save("L2-B1-07", "{\"cardsViewed\":0}", access);
        assertThat(zero).hasStatusOk();
        assertThat(zero).bodyJson().extractingPath("$.allViewed").isEqualTo(false);
    }

    @Test
    void 수업일_전이면_423() {
        MvcTestResult result = save(TOMORROW_CODE, "{\"memKnown\":1}", cookies.studentAccess(studentId, academyId));

        assertThat(result).hasStatus(423);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("LESSON_LOCKED");
    }

    @Test
    void 다른_학원_학생은_404이고_행이_안_생긴다() {
        long otherAcademy = data.academy("다른 학원");
        long otherClass = data.clazz(otherAcademy, "L2-B1", "다른 반", TestData.uniqueAccessCode(), "active");
        long otherStudent = data.student(otherAcademy, "박학생");
        data.enroll(otherClass, otherStudent);

        assertThat(save(TODAY_CODE, "{\"memKnown\":1}", cookies.studentAccess(otherStudent, otherAcademy)))
                .hasStatus(404);
        assertThat(save(TODAY_CODE, "{\"memKnown\":1}", cookies.studentAccess(studentId, otherAcademy)))
                .as("토큰의 학원과 학생 소속이 다름")
                .hasStatus(404);
        assertThat(jdbc.queryForObject(
                        "select count(*) from word_study where lesson_id = ?",
                        Integer.class,
                        data.lessonId(TODAY_CODE)))
                .isZero();
    }

    @Test
    void 회차_상세의_단어_수와_memKnown이_저장값을_보여준다() {
        data.lessonContent(TODAY_CODE, 1, 1, List.of("us_w"));
        save("{\"memKnown\":4}");

        MvcTestResult detail = mvc.get()
                .uri("/api/v1/student/lessons/" + TODAY_CODE)
                .cookie(cookies.studentAccess(studentId, academyId))
                .exchange();

        assertThat(detail).hasStatusOk();
        assertThat(detail).bodyJson().extractingPath("$.words.total").isEqualTo(WORDS);
        assertThat(detail).bodyJson().extractingPath("$.words.memKnown").isEqualTo(4);
    }
}
