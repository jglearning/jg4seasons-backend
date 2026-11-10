package com.jglearning.jg4seasons.clazz;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** 반 진도표 수업일(ADM-01). 개강일(포함)부터 수업 요일에 맞는 날짜를 차례로 뽑는다. 공휴일은 반영하지 않는다(휴강 처리로). */
final class ClassSchedule {

    private ClassSchedule() {}

    static List<LocalDate> dates(LocalDate startDate, Weekdays weekdays, int count) {
        List<LocalDate> dates = new ArrayList<>(count);
        LocalDate date = startDate;
        while (dates.size() < count) {
            if (weekdays.includes(date.getDayOfWeek())) {
                dates.add(date);
            }
            date = date.plusDays(1);
        }
        return dates;
    }
}
