package com.jglearning.jg4seasons.clazz;

import com.jglearning.jg4seasons.academy.api.AcademyDirectory;
import com.jglearning.jg4seasons.clazz.api.LessonAccess;
import com.jglearning.jg4seasons.clazz.api.StudentClass;
import com.jglearning.jg4seasons.clazz.api.StudentClasses;
import com.jglearning.jg4seasons.clazz.api.StudentLessonGate;
import com.jglearning.jg4seasons.infra.web.error.ApiException;
import com.jglearning.jg4seasons.infra.web.error.CommonErrorCode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
class StudentLessonGateService implements StudentLessonGate {

    private final AcademyDirectory academies;
    private final StudentClasses studentClasses;
    private final Clock clock;

    StudentLessonGateService(AcademyDirectory academies, StudentClasses studentClasses, Clock clock) {
        this.academies = academies;
        this.studentClasses = studentClasses;
        this.clock = clock;
    }

    @Override
    public void requireOpen(long academyId, long studentId, long bookId, long lessonId) {
        // 소속 반 중 그 회차의 권인 반 전부(이슈 #21 리뷰 7 a). 하나도 없으면 NOT_IN_CLASS → 404
        List<StudentClass> classes = studentClasses.currentClasses(academyId, studentId).stream()
                .filter(clazz -> clazz.bookId() == bookId)
                .toList();
        LocalDate today = LocalDate.now(clock.withZone(academies.zoneOf(academyId)));
        LessonAccess access = studentClasses.access(classes, lessonId, today);
        if (access == LessonAccess.NOT_IN_CLASS) {
            throw new ApiException(CommonErrorCode.NOT_FOUND, "회차를 찾을 수 없어요");
        }
        if (access == LessonAccess.LOCKED) {
            throw new ApiException(ClassErrorCode.LESSON_LOCKED, "수업일이 되면 열려요");
        }
    }
}
