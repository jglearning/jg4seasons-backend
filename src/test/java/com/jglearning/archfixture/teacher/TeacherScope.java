package com.jglearning.archfixture.teacher;

import com.jglearning.jg4seasons.clazz.api.ClassSchedules;
import java.time.LocalDate;

/** 정상: 범위 확인을 하는 TeacherScope만 진도표 api를 부른다. */
public class TeacherScope {
    private final ClassSchedules schedules;

    public TeacherScope(ClassSchedules schedules) {
        this.schedules = schedules;
    }

    public Object schedule(long academyId, long classId, LocalDate today) {
        return schedules.schedule(academyId, classId, today);
    }
}
