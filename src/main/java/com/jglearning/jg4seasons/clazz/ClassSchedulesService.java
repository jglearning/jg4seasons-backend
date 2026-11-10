package com.jglearning.jg4seasons.clazz;

import com.jglearning.jg4seasons.clazz.api.ClassSchedules;
import com.jglearning.jg4seasons.clazz.api.ScheduleEntry;
import com.jglearning.jg4seasons.clazz.persistence.ClassSessionStore;
import com.jglearning.jg4seasons.clazz.persistence.ClassSessionStore.SessionRow;
import com.jglearning.jg4seasons.infra.web.error.ApiException;
import com.jglearning.jg4seasons.infra.web.error.CommonErrorCode;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 진도표 조회·편집(ADR 0030). class_session에는 academy_id가 없어, 반을 학원 범위로 찾은 뒤에만 읽고 쓴다. */
@Service
@Transactional(readOnly = true)
class ClassSchedulesService implements ClassSchedules {

    private final ClassRepository classes;
    private final ClassSessionStore sessions;

    ClassSchedulesService(ClassRepository classes, ClassSessionStore sessions) {
        this.classes = classes;
        this.sessions = sessions;
    }

    @Override
    public List<ScheduleEntry> schedule(long academyId, long classId) {
        Clazz clazz = classes.findByAcademyIdAndId(academyId, classId)
                .orElseThrow(() -> new ApiException(CommonErrorCode.NOT_FOUND, "반을 찾을 수 없어요"));
        return sessions.findByClass(clazz.getId()).stream()
                .map(ClassSchedulesService::entry)
                .toList();
    }

    private static ScheduleEntry entry(SessionRow row) {
        return new ScheduleEntry(row.seq(), row.lessonId(), row.scheduledDate(), row.status(), row.note());
    }
}
