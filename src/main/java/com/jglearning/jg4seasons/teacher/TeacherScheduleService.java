package com.jglearning.jg4seasons.teacher;

import com.jglearning.jg4seasons.clazz.api.ScheduleChange;
import com.jglearning.jg4seasons.clazz.api.ScheduleEntry;
import com.jglearning.jg4seasons.content.api.LessonSummary;
import com.jglearning.jg4seasons.teacher.web.dto.SessionListResponse;
import com.jglearning.jg4seasons.teacher.web.dto.SessionListResponse.SessionItem;
import com.jglearning.jg4seasons.teacher.web.dto.TeacherLessonRef;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 4.5~4.7 교사 진도표(TCH-05, ADR 0030). 범위(활성 교사 → 담당 반, 관리자는 학원 전체)는 TeacherScope, 진도표 규칙·저장은 clazz. 여기서는 회차 code·제목을 붙여
 * 응답만 만든다.
 */
@Service
@Transactional(readOnly = true)
public class TeacherScheduleService {

    private final TeacherScope scope;

    TeacherScheduleService(TeacherScope scope) {
        this.scope = scope;
    }

    public SessionListResponse sessions(long academyId, long actorId, long classId) {
        TeacherScope.ScopedClass scoped = scope.classOf(academyId, actorId, classId);
        return response(scoped, scope.schedule(scoped));
    }

    /** 4.6. 바뀐 뒤 진도표 전체 */
    @Transactional
    public SessionListResponse update(
            long academyId,
            long actorId,
            long classId,
            int seq,
            LocalDate scheduledDate,
            String note,
            boolean shiftFollowing) {
        TeacherScope.ScopedClass scoped = scope.classOf(academyId, actorId, classId);
        return response(scoped, scope.reschedule(scoped, seq, new ScheduleChange(scheduledDate, note, shiftFollowing)));
    }

    /** 4.7. 바뀐 뒤 진도표 전체 */
    @Transactional
    public SessionListResponse cancel(
            long academyId, long actorId, long classId, int seq, String note, boolean shiftFollowing) {
        TeacherScope.ScopedClass scoped = scope.classOf(academyId, actorId, classId);
        return response(scoped, scope.cancel(scoped, seq, note, shiftFollowing));
    }

    private SessionListResponse response(TeacherScope.ScopedClass scoped, List<ScheduleEntry> entries) {
        Map<Long, LessonSummary> lessons = scope.lessonsOf(scoped);
        return new SessionListResponse(entries.stream()
                .map(entry -> {
                    LessonSummary lesson = lessons.get(entry.lessonId());
                    if (lesson == null) {
                        throw new IllegalStateException(
                                "진도표 회차가 그 반 권에 없다: class " + scoped.clazz().classId() + " lesson " + entry.lessonId());
                    }
                    return new SessionItem(
                            entry.seq(),
                            new TeacherLessonRef(lesson.code(), lesson.title()),
                            entry.scheduledDate(),
                            entry.status().value(),
                            entry.note());
                })
                .toList());
    }
}
