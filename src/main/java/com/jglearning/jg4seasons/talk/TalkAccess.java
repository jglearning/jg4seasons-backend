package com.jglearning.jg4seasons.talk;

import com.jglearning.jg4seasons.academy.api.AcademyDirectory;
import com.jglearning.jg4seasons.clazz.api.ClassLesson;
import com.jglearning.jg4seasons.clazz.api.StudentClass;
import com.jglearning.jg4seasons.clazz.api.StudentClasses;
import com.jglearning.jg4seasons.clazz.api.TodayLesson;
import com.jglearning.jg4seasons.content.api.BookDirectory;
import com.jglearning.jg4seasons.content.api.LessonCatalog;
import com.jglearning.jg4seasons.content.api.LessonWord;
import com.jglearning.jg4seasons.infra.web.error.ApiException;
import com.jglearning.jg4seasons.infra.web.error.CommonErrorCode;
import com.jglearning.jg4seasons.talk.persistence.TalkStudentAccessStore;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * 프리토킹 공통 앞부분: 학원 활성(401) → 기능 켜짐(404 FEATURE_DISABLED) → 학생 허용(404 FEATURE_DISABLED, ADR 0040), 학생의 레벨·오늘 회차·회차 단어. 학생·학원은 JWT에서만. 화면에 보이는 단어(config
 * lessonWords)와 지시문에 넣는 단어가 같은 목록이 되도록 둘 다 여기서 얻는다(추가 지시 가-1)
 */
@Component
class TalkAccess {

    /**
     * 학생의 대화 재료. 오늘 회차가 없으면(개강 전) lessonId 비어 있고 words 빈 목록
     *
     * @param words 오늘 회차 단어 전부(교재 순서, 숙어 포함)
     */
    record TalkContext(StudentClass clazz, TalkLevel level, Optional<Long> lessonId, List<LessonWord> words) {

        TalkContext {
            words = List.copyOf(words);
        }

        List<String> english() {
            return words.stream().map(LessonWord::english).toList();
        }
    }

    private final AcademyDirectory academies;
    private final StudentClasses studentClasses;
    private final BookDirectory books;
    private final LessonCatalog catalog;
    private final TalkStudentAccessStore studentAccess;
    private final Clock clock;

    TalkAccess(
            AcademyDirectory academies,
            StudentClasses studentClasses,
            BookDirectory books,
            LessonCatalog catalog,
            TalkStudentAccessStore studentAccess,
            Clock clock) {
        this.academies = academies;
        this.studentClasses = studentClasses;
        this.books = books;
        this.catalog = catalog;
        this.studentAccess = studentAccess;
        this.clock = clock;
    }

    /** 학원 스위치와 학생 허용 둘 다 켜져야 한다(AND). 꺼진 학생은 학원이 꺼진 것과 같은 응답 — 화면은 진입점을 숨긴다 */
    void requireEnabled(long academyId, long studentId) {
        if (!academies.isActive(academyId)) {
            throw new ApiException(CommonErrorCode.UNAUTHORIZED);
        }
        if (!academies.talkEnabled(academyId)) {
            throw new ApiException(CommonErrorCode.FEATURE_DISABLED, "프리토킹을 쓸 수 없는 학원이에요");
        }
        if (!studentAccess.isEnabled(academyId, studentId)) {
            throw new ApiException(CommonErrorCode.FEATURE_DISABLED, "프리토킹을 쓸 수 없어요");
        }
    }

    boolean entitled(long academyId) {
        return academies.talkEntitled(academyId);
    }

    /** 현재 반(홈과 같이 첫 반)의 레벨과 오늘 회차. 반이 없으면 404(학생 홈과 같은 처리) */
    TalkContext context(long academyId, long studentId) {
        List<StudentClass> classes = studentClasses.currentClasses(academyId, studentId);
        if (classes.isEmpty()) {
            throw new ApiException(CommonErrorCode.NOT_FOUND, "반을 찾을 수 없어요");
        }
        StudentClass clazz = classes.get(0);
        TalkLevel level = TalkLevel.ofLevelCode(books.summaryOf(clazz.bookId()).levelCode());
        Optional<Long> lessonId =
                TodayLesson.of(studentClasses.lessons(clazz, today(academyId))).map(ClassLesson::lessonId);
        List<LessonWord> words = lessonId.map(catalog::words).orElse(List.of());
        return new TalkContext(clazz, level, lessonId, words);
    }

    /** 학원 타임존 오늘 0시(UTC 시점) — 하루 한도의 시작 */
    Instant startOfToday(long academyId) {
        ZoneId zone = academies.zoneOf(academyId);
        return today(academyId).atStartOfDay(zone).toInstant();
    }

    private LocalDate today(long academyId) {
        return LocalDate.now(clock.withZone(academies.zoneOf(academyId)));
    }
}
