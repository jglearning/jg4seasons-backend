package com.jglearning.archfixture.teacher;

import com.jglearning.jg4seasons.clazz.api.ClassSchedules;
import java.time.LocalDate;

/** 위반: TeacherScope를 거치지 않고 진도표 api를 직접 불러 담당 반 확인을 건너뛴다. */
public class ScheduleBypasser {
    private final ClassSchedules schedules;

    public ScheduleBypasser(ClassSchedules schedules) {
        this.schedules = schedules;
    }

    public Object cancelAnyClass(long academyId, long classId) {
        return schedules.cancel(academyId, classId, 1, null, true, LocalDate.of(2026, 11, 11));
    }
}
