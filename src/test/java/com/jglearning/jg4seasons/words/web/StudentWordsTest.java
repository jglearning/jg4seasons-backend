package com.jglearning.jg4seasons.words.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.jglearning.jg4seasons.IntegrationTest;
import com.jglearning.jg4seasons.MutableClock;
import com.jglearning.jg4seasons.TestData;
import com.jglearning.jg4seasons.auth.AuthCookieSupport;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.annotation.Transactional;

/**
 * 2.4 GET /student/lessons/{lessonCode}/words — 회차 단어(S-06·S-06b, ADR 0029). 오늘 = 서울 2026-11-10. 반은 L2-B1, 진도표 5회차 오늘·6회차
 * 내일. 접근 규칙은 2.3과 같은 게이트(다른 학원·다른 권 404, 미래 423). 단어는 지어낸 것.
 */
@IntegrationTest
@Transactional
class StudentWordsTest {

    static final String TODAY_CODE = "L2-B1-05";
    static final String TOMORROW_CODE = "L2-B1-06";
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
        academyId = data.academy("단어 학원");
        classId = data.clazz(academyId, "L2-B1", "겨울 그린 A반", TestData.uniqueAccessCode(), "active");
        studentId = data.student(academyId, "김학생");
        data.enroll(classId, studentId);
        data.session(classId, 5, TODAY_CODE, LocalDate.of(2026, 11, 10), "scheduled");
        data.session(classId, 6, TOMORROW_CODE, LocalDate.of(2026, 11, 11), "scheduled");
    }

    @AfterEach
    void resetClock() {
        clock.reset();
    }

    private static String url(String code) {
        return "/api/v1/student/lessons/" + code + "/words";
    }

    private MvcTestResult get(String code, Cookie... cookieList) {
        return mvc.get().uri(url(code)).cookie(cookieList).exchange();
    }

    private MvcTestResult getAsStudent(String code) {
        return get(code, cookies.studentAccess(studentId, academyId));
    }

    @Test
    void 교재_순서로_출처와_함께_준다() {
        // 어휘책에만 있는 단어(음원·해석 없음) → 녹음 자료에도 있는 어휘책 단어 → 어휘책에 없는 회차 어휘
        data.word(TODAY_CODE, 1, "glimmer", "book", "pron", false);
        data.word(TODAY_CODE, 2, "brisk", "book", "adj", true);
        data.word(TODAY_CODE, 3, "wander off", "extra", "phr", true);

        MvcTestResult result = getAsStudent(TODAY_CODE);

        assertThat(result).hasStatusOk();
        var json = assertThat(result).bodyJson();
        json.extractingPath("$.total").isEqualTo(3);
        json.extractingPath("$.items[*].order").isEqualTo(List.of(1, 2, 3));
        json.extractingPath("$.items[*].english").isEqualTo(List.of("glimmer", "brisk", "wander off"));
        json.extractingPath("$.items[*].source").isEqualTo(List.of("book", "book", "extra"));
        json.extractingPath("$.items[*].pos").isEqualTo(List.of("pron", "adj", "phr"));
        json.extractingPath("$.items[0].korean").isEqualTo("glimmer 뜻");
        json.extractingPath("$.items[0].example").isEqualTo("A made-up line with glimmer.");
        json.extractingPath("$.items[0].exampleKo").isNull();
        json.extractingPath("$.items[0].audio").isNull();
        json.extractingPath("$.items[0].exampleAudio").isNull();
        json.extractingPath("$.items[1].exampleKo").isEqualTo("지어낸 예문 2");
        json.extractingPath("$.items[1].audio.audioUrl").isEqualTo("/audio/L2-B1-05/L2-B1-05_w02_us_w.mp3");
        json.extractingPath("$.items[1].audio.durationMs").isEqualTo(900);
        json.extractingPath("$.items[1].exampleAudio.audioUrl").isEqualTo("/audio/L2-B1-05/L2-B1-05_w02_ex_us_w.mp3");
        json.extractingPath("$.items[1].exampleAudio.durationMs").isEqualTo(2400);
        Long trackId = jdbc.queryForObject(
                "select track_id from word where lesson_id = ? and sort_order = 2",
                Long.class,
                data.lessonId(TODAY_CODE));
        json.extractingPath("$.items[1].audio.trackId").isEqualTo(trackId.intValue());
        json.doesNotHavePath("$.items[0].passageNo");
    }

    @Test
    void 단어가_없는_회차는_빈_목록() {
        MvcTestResult result = getAsStudent(TODAY_CODE);

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.items").isEqualTo(List.of());
        assertThat(result).bodyJson().extractingPath("$.total").isEqualTo(0);
    }

    @Test
    void 수업일_전이면_423_LESSON_LOCKED() {
        data.word(TOMORROW_CODE, 1, "glimmer", "book", "n", false);

        MvcTestResult result = getAsStudent(TOMORROW_CODE);

        assertThat(result).hasStatus(423);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("LESSON_LOCKED");
    }

    @Test
    void 다른_학원_학생은_404() {
        long otherAcademy = data.academy("다른 학원");
        long otherClass = data.clazz(otherAcademy, "L2-B1", "다른 반", TestData.uniqueAccessCode(), "active");
        long otherStudent = data.student(otherAcademy, "박학생");
        data.enroll(otherClass, otherStudent);

        assertThat(get(TODAY_CODE, cookies.studentAccess(otherStudent, otherAcademy)))
                .as("자기 학원 반에 진도표가 없음")
                .hasStatus(404);
        assertThat(get(TODAY_CODE, cookies.studentAccess(studentId, otherAcademy)))
                .as("토큰의 학원과 학생 소속이 다름")
                .hasStatus(404);
    }

    @Test
    void 다른_권_반_학생은_404() {
        long l3Class = data.clazz(academyId, "L3-B1", "블루 반", TestData.uniqueAccessCode(), "active");
        long l3Student = data.student(academyId, "최학생");
        data.enroll(l3Class, l3Student);
        data.session(l3Class, 5, "L3-B1-05", LocalDate.of(2026, 11, 10), "scheduled");

        assertThat(get(TODAY_CODE, cookies.studentAccess(l3Student, academyId))).hasStatus(404);
    }

    @Test
    void 같은_권_두_반_중_한_반에서_열렸으면_200() {
        long second = data.clazz(academyId, "L2-B1", "토요 반", TestData.uniqueAccessCode(), "active");
        data.enroll(second, studentId);
        data.session(second, 6, TOMORROW_CODE, LocalDate.of(2026, 11, 9), "scheduled");

        assertThat(getAsStudent(TOMORROW_CODE)).as("첫 반에서는 내일, 둘째 반에서는 어제").hasStatusOk();
    }

    @Test
    void 없는_회차는_404() {
        assertThat(getAsStudent("L9-B9-99")).hasStatus(404);
    }
}
