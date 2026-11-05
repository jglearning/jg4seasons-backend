package com.jglearning.jg4seasons.clazz;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jglearning.jg4seasons.IntegrationTest;
import com.jglearning.jg4seasons.MutableClock;
import com.jglearning.jg4seasons.TestData;
import com.jglearning.jg4seasons.clazz.api.ClassChange;
import com.jglearning.jg4seasons.clazz.api.ClassDetail;
import com.jglearning.jg4seasons.clazz.api.ClassManagement;
import com.jglearning.jg4seasons.clazz.api.ClassStatus;
import com.jglearning.jg4seasons.clazz.api.NewClass;
import com.jglearning.jg4seasons.clazz.persistence.AccessCodeLookup;
import com.jglearning.jg4seasons.clazz.persistence.ClassSessionStore;
import com.jglearning.jg4seasons.clazz.persistence.ClassSessionStore.SessionRow;
import com.jglearning.jg4seasons.content.api.BookDirectory;
import com.jglearning.jg4seasons.infra.web.error.ApiException;
import com.jglearning.jg4seasons.infra.web.error.CommonErrorCode;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** 반 생성(진도표 24행 + 가입 코드)·조회·변경·가입 코드 재발급. 다른 학원 반은 404. */
@IntegrationTest
@Transactional
class ClassManagementTest {

    private static final LocalDate MONDAY = LocalDate.of(2026, 11, 9);

    @Autowired
    ClassManagement classes;

    @Autowired
    BookDirectory books;

    @Autowired
    ClassSessionStore sessions;

    @Autowired
    AccessCodeLookup accessCodes;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    MutableClock clock;

    @Autowired
    EntityManager entityManager;

    TestData data;
    long academyId;
    long otherAcademy;

    @BeforeEach
    void setUp() {
        data = new TestData(jdbc);
        academyId = data.academy("반 관리 학원");
        otherAcademy = data.academy("다른 학원");
    }

    @AfterEach
    void resetClock() {
        clock.reset();
    }

    private NewClass newClass(String name, List<Integer> weekdays) {
        var lessons = books.findLessons("L2-B1").orElseThrow();
        return new NewClass(name, lessons, MONDAY, weekdays);
    }

    @Test
    void 반을_만들면_진도표_24행과_가입_코드가_생긴다() {
        clock.set(Instant.parse("2026-11-05T01:02:03.999Z"));
        var lessons = books.findLessons("L2-B1").orElseThrow();

        ClassDetail created = classes.create(academyId, newClass("  겨울 그린 A반  ", List.of(3, 1)));

        assertThat(created.name()).isEqualTo("겨울 그린 A반");
        assertThat(created.bookId()).isEqualTo(lessons.bookId());
        assertThat(created.startDate()).isEqualTo(MONDAY);
        assertThat(created.weekdays()).containsExactly(1, 3);
        assertThat(created.accessCode()).matches("[A-Z2-9]{8}");
        assertThat(created.codeIssuedAt()).isEqualTo(Instant.parse("2026-11-05T01:02:03Z"));
        assertThat(created.allowFutureLessons()).isFalse();
        assertThat(created.status()).isEqualTo(ClassStatus.ACTIVE);
        assertThat(created.studentCount()).isZero();
        assertThat(jdbc.queryForObject("select weekdays from `class` where id = ?", String.class, created.id()))
                .isEqualTo("1010000");

        List<SessionRow> rows = sessions.findByClass(created.id());
        List<LocalDate> expectedDates =
                ClassSchedule.dates(MONDAY, Weekdays.fromNumbers(List.of(1, 3)), ClassSchedule.SESSION_COUNT);
        assertThat(rows).hasSize(24);
        for (int i = 0; i < 24; i++) {
            SessionRow row = rows.get(i);
            assertThat(row.seq()).isEqualTo(i + 1);
            assertThat(row.lessonId()).isEqualTo(lessons.lessonIdsByNumber().get(i));
            assertThat(row.scheduledDate()).isEqualTo(expectedDates.get(i));
            assertThat(row.status()).isEqualTo("scheduled");
        }
    }

    @Test
    void 만든_가입_코드로_입장할_반을_찾는다() {
        ClassDetail created = classes.create(academyId, newClass("A반", List.of(1)));

        assertThat(accessCodes.findActiveClass(created.accessCode()))
                .contains(new AccessCodeLookup.Match(created.id(), academyId));
    }

    @Test
    void 가입_코드_존재는_상태와_학원에_무관() {
        String finishedCode = TestData.uniqueAccessCode();
        data.clazz(otherAcademy, "L2-B1", "끝난 반", finishedCode, "finished");

        assertThat(accessCodes.exists(finishedCode)).isTrue();
        assertThat(accessCodes.exists("ZZZZZZZZ")).isFalse();
    }

    @Test
    void 이름은_공백을_빼고_1자에서_50자() {
        assertValidation(() -> classes.create(academyId, newClass("   ", List.of(1))), "name");
        assertValidation(() -> classes.create(academyId, newClass("가".repeat(51), List.of(1))), "name");
        assertThat(classes.create(academyId, newClass("가".repeat(50), List.of(1)))
                        .name())
                .hasSize(50);
    }

    @Test
    void 요일이_잘못되면_반도_진도표도_안_생긴다() {
        assertValidation(() -> classes.create(academyId, newClass("A반", List.of(8))), "weekdays");

        assertThat(jdbc.queryForObject("select count(*) from `class` where academy_id = ?", Integer.class, academyId))
                .isZero();
    }

    @Test
    void 목록은_id_순이고_상태로_거른다() {
        long first = classes.create(academyId, newClass("A반", List.of(1))).id();
        long second = classes.create(academyId, newClass("B반", List.of(2))).id();
        classes.change(
                academyId,
                second,
                new ClassChange(Optional.empty(), Optional.of(ClassStatus.FINISHED), Optional.empty()));
        classes.create(otherAcademy, newClass("다른 반", List.of(1)));

        assertThat(classes.list(academyId, Optional.empty()))
                .extracting(ClassDetail::id)
                .containsExactly(first, second);
        assertThat(classes.list(academyId, Optional.of(ClassStatus.ACTIVE)))
                .extracting(ClassDetail::id)
                .containsExactly(first);
        assertThat(classes.list(academyId, Optional.of(ClassStatus.FINISHED)))
                .extracting(ClassDetail::id)
                .containsExactly(second);
    }

    @Test
    void 다른_학원_반은_404() {
        long other = classes.create(otherAcademy, newClass("다른 반", List.of(1))).id();
        ClassChange rename = new ClassChange(Optional.of("바꿈"), Optional.empty(), Optional.empty());

        assertNotFound(() -> classes.find(academyId, other));
        assertNotFound(() -> classes.change(academyId, other, rename));
        assertNotFound(() -> classes.regenerateAccessCode(academyId, other));
        assertNotFound(() -> classes.find(academyId, -1L));
        assertThat(classes.find(otherAcademy, other).name()).isEqualTo("다른 반");
    }

    @Test
    void 빠진_칸은_바꾸지_않는다() {
        ClassDetail created = classes.create(academyId, newClass("A반", List.of(1)));

        ClassDetail renamed = classes.change(
                academyId, created.id(), new ClassChange(Optional.of(" B반 "), Optional.empty(), Optional.empty()));
        ClassDetail changed = classes.change(
                academyId,
                created.id(),
                new ClassChange(Optional.empty(), Optional.of(ClassStatus.ARCHIVED), Optional.of(true)));

        assertThat(renamed.name()).isEqualTo("B반");
        assertThat(renamed.status()).isEqualTo(ClassStatus.ACTIVE);
        assertThat(changed.name()).isEqualTo("B반");
        assertThat(changed.status()).isEqualTo(ClassStatus.ARCHIVED);
        assertThat(changed.allowFutureLessons()).isTrue();
        assertThat(changed.accessCode()).isEqualTo(created.accessCode());
        assertValidation(
                () -> classes.change(
                        academyId, created.id(), new ClassChange(Optional.of(" "), Optional.empty(), Optional.empty())),
                "name");
    }

    @Test
    void 재발급하면_코드와_발급_시각이_바뀐다() {
        clock.set(Instant.parse("2026-11-05T01:00:00Z"));
        ClassDetail created = classes.create(academyId, newClass("A반", List.of(1)));
        clock.set(Instant.parse("2026-11-06T09:30:15.500Z"));

        ClassDetail reissued = classes.regenerateAccessCode(academyId, created.id());

        assertThat(reissued.accessCode()).isNotEqualTo(created.accessCode()).matches("[A-Z2-9]{8}");
        assertThat(reissued.codeIssuedAt()).isEqualTo(Instant.parse("2026-11-06T09:30:15Z"));
        entityManager.flush();
        entityManager.clear();
        assertThat(accessCodes.exists(created.accessCode())).isFalse();
        ClassDetail reread = classes.find(academyId, created.id());
        assertThat(reread.accessCode()).isEqualTo(reissued.accessCode());
        assertThat(reread.weekdays()).as("DB 비트 문자열에서 다시 읽은 요일").containsExactly(1);
    }

    @Test
    void 학생_수는_현재_소속만() {
        ClassDetail created = classes.create(academyId, newClass("A반", List.of(1)));
        long staying = data.student(academyId, "학생1");
        long left = data.student(academyId, "학생2");
        data.enroll(created.id(), staying);
        data.enroll(created.id(), left);
        data.leave(created.id(), left);

        assertThat(classes.find(academyId, created.id()).studentCount()).isEqualTo(1);
    }

    private static void assertValidation(Runnable action, String field) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(ApiException.class, e -> {
            assertThat(e.errorCode()).isEqualTo(CommonErrorCode.VALIDATION);
            assertThat(e.field()).isEqualTo(field);
        });
    }

    private static void assertNotFound(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(
                        ApiException.class, e -> assertThat(e.errorCode()).isEqualTo(CommonErrorCode.NOT_FOUND));
    }
}
