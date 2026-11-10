package com.jglearning.jg4seasons.clazz;

import com.jglearning.jg4seasons.clazz.api.ClassSchedules;
import com.jglearning.jg4seasons.clazz.api.ScheduleChange;
import com.jglearning.jg4seasons.clazz.api.ScheduleEntry;
import com.jglearning.jg4seasons.clazz.persistence.ClassSessionStore;
import com.jglearning.jg4seasons.clazz.persistence.ClassSessionStore.SessionRow;
import com.jglearning.jg4seasons.clazz.persistence.ClassSessionStore.SessionWrite;
import com.jglearning.jg4seasons.infra.web.error.ApiException;
import com.jglearning.jg4seasons.infra.web.error.CommonErrorCode;
import java.time.LocalDate;
import java.util.ArrayList;
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

    @Override
    @Transactional
    public List<ScheduleEntry> reschedule(
            long academyId, long classId, int seq, ScheduleChange change, LocalDate today) {
        return edit(
                academyId,
                classId,
                (rows, weekdays) -> ScheduleEdit.reschedule(
                        rows, seq, change.scheduledDate(), change.note(), change.shiftFollowing(), weekdays, today));
    }

    @Override
    @Transactional
    public List<ScheduleEntry> cancel(
            long academyId, long classId, int seq, String note, boolean shiftFollowing, LocalDate today) {
        return edit(
                academyId,
                classId,
                (rows, weekdays) -> ScheduleEdit.cancel(rows, seq, note, shiftFollowing, weekdays, today));
    }

    /** 편집 규칙 하나(ScheduleEdit.cancel·reschedule) */
    private interface Edit {
        List<ScheduleEdit.Row> apply(List<ScheduleEdit.Row> rows, Weekdays weekdays);
    }

    /**
     * 같은 반 편집은 반 행 잠금으로 줄 세운다: 잠금(학원 범위, 없으면 404) → 진도표 잠금 읽기(최신 행 — 앞선 일반 읽기 스냅숏이 아니라) → 규칙 → 바뀌었으면 쓰기.
     * 결과 목록의 순서가 새 seq
     */
    private List<ScheduleEntry> edit(long academyId, long classId, Edit edit) {
        Clazz clazz = classes.findByAcademyIdAndIdForUpdate(academyId, classId)
                .orElseThrow(() -> new ApiException(CommonErrorCode.NOT_FOUND, "반을 찾을 수 없어요"));
        List<ScheduleEdit.Row> rows = sessions.findByClassForUpdate(clazz.getId()).stream()
                .map(row ->
                        new ScheduleEdit.Row(row.id(), row.lessonId(), row.scheduledDate(), row.status(), row.note()))
                .toList();
        List<ScheduleEdit.Row> result = edit.apply(rows, clazz.getWeekdays());
        if (!result.equals(rows)) {
            List<SessionWrite> writes = new ArrayList<>(result.size());
            for (int i = 0; i < result.size(); i++) {
                ScheduleEdit.Row row = result.get(i);
                writes.add(new SessionWrite(row.id(), i + 1, row.lessonId(), row.date(), row.status(), row.note()));
            }
            sessions.replace(clazz.getId(), writes);
        }
        List<ScheduleEntry> entries = new ArrayList<>(result.size());
        for (int i = 0; i < result.size(); i++) {
            ScheduleEdit.Row row = result.get(i);
            entries.add(new ScheduleEntry(i + 1, row.lessonId(), row.date(), row.status(), row.note()));
        }
        return entries;
    }

    private static ScheduleEntry entry(SessionRow row) {
        return new ScheduleEntry(row.seq(), row.lessonId(), row.scheduledDate(), row.status(), row.note());
    }
}
