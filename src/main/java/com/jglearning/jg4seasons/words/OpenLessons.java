package com.jglearning.jg4seasons.words;

import com.jglearning.jg4seasons.academy.api.AcademyDirectory;
import com.jglearning.jg4seasons.clazz.api.StudentLessonGate;
import com.jglearning.jg4seasons.content.api.LessonCatalog;
import com.jglearning.jg4seasons.content.api.LessonContent;
import com.jglearning.jg4seasons.infra.web.error.ApiException;
import com.jglearning.jg4seasons.infra.web.error.CommonErrorCode;
import org.springframework.stereotype.Component;

/** 2.4·2.5 공통: 학원 활성(아니면 401) → 회차(없으면 404) → 소속 반 진도표에서 열렸는가(2.3과 같은 게이트, 404·423). */
@Component
class OpenLessons {

    private final AcademyDirectory academies;
    private final LessonCatalog catalog;
    private final StudentLessonGate gate;

    OpenLessons(AcademyDirectory academies, LessonCatalog catalog, StudentLessonGate gate) {
        this.academies = academies;
        this.catalog = catalog;
        this.gate = gate;
    }

    LessonContent require(long academyId, long studentId, String lessonCode) {
        if (!academies.isActive(academyId)) {
            throw new ApiException(CommonErrorCode.UNAUTHORIZED);
        }
        LessonContent lesson = catalog.findByCode(lessonCode)
                .orElseThrow(() -> new ApiException(CommonErrorCode.NOT_FOUND, "회차를 찾을 수 없어요"));
        gate.requireOpen(academyId, studentId, lesson.bookId(), lesson.lessonId());
        return lesson;
    }
}
