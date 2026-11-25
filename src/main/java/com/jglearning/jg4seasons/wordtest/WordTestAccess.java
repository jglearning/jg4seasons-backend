package com.jglearning.jg4seasons.wordtest;

import com.jglearning.jg4seasons.academy.api.AcademyDirectory;
import com.jglearning.jg4seasons.clazz.api.StudentLessonGate;
import com.jglearning.jg4seasons.content.api.LessonCatalog;
import com.jglearning.jg4seasons.content.api.LessonContent;
import com.jglearning.jg4seasons.infra.web.error.ApiException;
import com.jglearning.jg4seasons.infra.web.error.CommonErrorCode;
import org.springframework.stereotype.Component;

/**
 * 학생 단어시험 API의 공통 앞부분 검사. 순서: 학원 활성(401) → 기능 켜짐(404 FEATURE_DISABLED) → 회차 있음(404 NOT_FOUND) → 열림(StudentLessonGate: 404·403
 * LICENSE_INACTIVE·423 LESSON_LOCKED). 학생·학원은 JWT에서만.
 */
@Component
class WordTestAccess {

    /** 열린 회차 */
    record LessonRef(long lessonId, String code) {}

    private final AcademyDirectory academies;
    private final LessonCatalog catalog;
    private final StudentLessonGate gate;

    WordTestAccess(AcademyDirectory academies, LessonCatalog catalog, StudentLessonGate gate) {
        this.academies = academies;
        this.catalog = catalog;
        this.gate = gate;
    }

    /** 학원 활성 → 기능 켜짐. 회차가 없는 API(제출 조회)도 이것부터 */
    void requireEnabled(long academyId) {
        if (!academies.isActive(academyId)) {
            throw new ApiException(CommonErrorCode.UNAUTHORIZED);
        }
        if (!academies.wordTestEnabled(academyId)) {
            // 문구는 옮기기 전 title 그대로(화면은 detail ?? title)
            throw new ApiException(CommonErrorCode.FEATURE_DISABLED, "단어시험을 쓸 수 없는 학원이에요");
        }
    }

    LessonRef requireOpenLesson(long academyId, long studentId, String lessonCode) {
        requireEnabled(academyId);
        LessonContent lesson = catalog.findByCode(lessonCode)
                .orElseThrow(() -> new ApiException(CommonErrorCode.NOT_FOUND, "회차를 찾을 수 없어요"));
        gate.requireOpen(academyId, studentId, lesson.bookId(), lesson.lessonId());
        return new LessonRef(lesson.lessonId(), lesson.code());
    }
}
