package com.jglearning.jg4seasons.talk.web;

import static com.jglearning.jg4seasons.talk.web.TalkTestSupport.assertProblem;
import static com.jglearning.jg4seasons.talk.web.TalkTestSupport.config;
import static com.jglearning.jg4seasons.talk.web.TalkTestSupport.create;
import static com.jglearning.jg4seasons.talk.web.TalkTestSupport.features;
import static org.assertj.core.api.Assertions.assertThat;

import com.jglearning.jg4seasons.IntegrationTest;
import com.jglearning.jg4seasons.MutableClock;
import com.jglearning.jg4seasons.TestData;
import com.jglearning.jg4seasons.auth.AuthCookieSupport;
import com.jglearning.jg4seasons.auth.UserRole;
import com.jglearning.jg4seasons.talk.realtime.FakeRealtimeSessionIssuer;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.annotation.Transactional;

/** GET /talk/config(계약 D-1 + 추가 지시 가-1 lessonWords) */
@IntegrationTest
@Transactional
class TalkConfigTest {

    static final String TODAY_LESSON = "L2-B1-03";

    @Autowired
    MockMvcTester mvc;

    @Autowired
    AuthCookieSupport cookies;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    MutableClock clock;

    @Autowired
    FakeRealtimeSessionIssuer issuer;

    TestData data;
    long academyId;
    long classId;
    long studentId;

    @BeforeEach
    void setUp() {
        clock.set(Instant.parse("2026-11-26T03:00:00Z")); // 서울 11/26 12:00
        issuer.reset(); // 가짜 발급기는 컨텍스트 하나에 공유 — 앞 테스트의 실패 예약이 남지 않게(리뷰 12)
        data = new TestData(jdbc);
        academyId = data.academy("대화 학원");
        features(jdbc, academyId, true, true);
        classId = data.clazz(academyId, "L2-B1", "그린 반", TestData.uniqueAccessCode(), "active");
        studentId = data.student(academyId, "대화 학생");
        data.enroll(classId, studentId);
        data.session(classId, 1, "L2-B1-02", LocalDate.of(2026, 11, 24), "scheduled");
        data.session(classId, 2, TODAY_LESSON, LocalDate.of(2026, 11, 26), "scheduled");
        data.session(classId, 3, "L2-B1-04", LocalDate.of(2026, 11, 28), "scheduled");
        data.word(TODAY_LESSON, 1, "cheerful", "book", "adj", false);
        data.word(TODAY_LESSON, 2, "make friends", "extra", "phr", false);
        data.word("L2-B1-04", 1, "tomorrow word", "book", "n", false);
    }

    @AfterEach
    void tearDown() {
        clock.reset();
        issuer.reset();
    }

    @Test
    void 켜지고_구독한_학원은_레벨_시간_남은_횟수_주제_오늘_회차_단어() {
        MvcTestResult result = config(mvc, student());

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.entitled").isEqualTo(true);
        assertThat(result).bodyJson().extractingPath("$.level").isEqualTo("green");
        assertThat(result).bodyJson().extractingPath("$.maxSeconds").isEqualTo(180);
        assertThat(result).bodyJson().extractingPath("$.remainingToday").isEqualTo(3);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.topics")
                .isEqualTo(List.of(
                        Map.of("code", "lesson", "title", "오늘 배운 단어로 이야기하기"),
                        Map.of("code", "day", "title", "오늘 하루"),
                        Map.of("code", "likes", "title", "내가 좋아하는 것")));
        // 숙어(띄어쓰기)도 그대로, 교재 순서. 다음 회차 단어는 없다
        assertThat(result)
                .bodyJson()
                .extractingPath("$.lessonWords")
                .isEqualTo(List.of(
                        Map.of("english", "cheerful", "korean", "cheerful 뜻"),
                        Map.of("english", "make friends", "korean", "make friends 뜻")));
    }

    @Test
    void 구독이_없으면_200에_entitled_false이고_단어는_빈_배열() {
        features(jdbc, academyId, true, false);

        MvcTestResult result = config(mvc, student());

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.entitled").isEqualTo(false);
        assertThat(result).bodyJson().extractingPath("$.level").isEqualTo("green");
        assertThat(result).bodyJson().extractingPath("$.remainingToday").isEqualTo(3);
        assertThat(result).bodyJson().extractingPath("$.topics").asArray().hasSize(3);
        assertThat(result).bodyJson().extractingPath("$.lessonWords").asArray().isEmpty();
    }

    @Test
    void 오늘_회차가_없으면_단어는_빈_배열() {
        clock.set(Instant.parse("2026-11-20T03:00:00Z")); // 첫 수업일(11/24) 전

        MvcTestResult result = config(mvc, student());

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.lessonWords").asArray().isEmpty();
    }

    @Test
    void 오늘_회차에_단어가_없으면_빈_배열() {
        clock.set(Instant.parse("2026-11-24T03:00:00Z")); // 오늘 회차 L2-B1-02 — 단어 없음

        MvcTestResult result = config(mvc, student());

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.lessonWords").asArray().isEmpty();
    }

    @Test
    void 세션을_만들면_남은_횟수가_준다() {
        create(mvc, student(), UUID.randomUUID().toString(), "day");

        assertThat(config(mvc, student()))
                .bodyJson()
                .extractingPath("$.remainingToday")
                .isEqualTo(2);
    }

    @Test
    void 다른_학생의_세션과_발급에_실패한_세션은_남은_횟수에서_빠지지_않는다() {
        // 리뷰 7: 표에 학원 칸이 없어 학생 조건이 유일한 격리 — 같은 반 친구가 쓴 횟수가 내 한도를 깎지 않게
        long friend = data.student(academyId, "친구");
        data.enroll(classId, friend);
        create(mvc, cookies.studentAccess(friend, academyId), UUID.randomUUID().toString(), "day");
        create(mvc, cookies.studentAccess(friend, academyId), UUID.randomUUID().toString(), "day");
        issuer.failNext();
        create(mvc, student(), UUID.randomUUID().toString(), "day");

        assertThat(config(mvc, student()))
                .bodyJson()
                .extractingPath("$.remainingToday")
                .isEqualTo(3);
    }

    @Test
    void 정지된_학원은_401() {
        jdbc.update("update academy set status = 'suspended' where id = ?", academyId);

        assertThat(config(mvc, student())).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void 기능이_꺼진_학원은_404_FEATURE_DISABLED() {
        features(jdbc, academyId, false, false);

        assertProblem(config(mvc, student()), HttpStatus.NOT_FOUND, "FEATURE_DISABLED");
    }

    @Test
    void 반이_없는_학생은_404() {
        long loner = data.student(academyId, "반 없는 학생");

        assertProblem(config(mvc, cookies.studentAccess(loner, academyId)), HttpStatus.NOT_FOUND, "NOT_FOUND");
    }

    @Test
    void 교사는_403() {
        long teacher = data.staff(academyId, "교사", "teacher");

        assertThat(config(mvc, cookies.access(teacher, UserRole.TEACHER, academyId)))
                .hasStatus(HttpStatus.FORBIDDEN);
    }

    private jakarta.servlet.http.Cookie student() {
        return cookies.studentAccess(studentId, academyId);
    }
}
