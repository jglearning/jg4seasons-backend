package com.jglearning.jg4seasons.clazz;

import com.jglearning.jg4seasons.infra.web.error.ApiException;
import java.time.DayOfWeek;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * 반의 수업 요일. API는 1(월)~7(일) 숫자 배열, DB(class.weekdays)는 월~일 7자 비트 문자열(`1010000` = 월·수). 하나 이상, 겹침 없음.
 */
final class Weekdays {

    private static final String INVALID = "수업 요일은 1(월)~7(일)에서 겹치지 않게 하나 이상 골라 주세요";

    private final Set<DayOfWeek> days;

    private Weekdays(Set<DayOfWeek> days) {
        this.days = days;
    }

    /** 요청 값. null·빈 목록·1~7 밖·중복이면 400 VALIDATION(field weekdays) */
    static Weekdays fromNumbers(List<Integer> numbers) {
        if (numbers == null || numbers.isEmpty()) {
            throw ApiException.validation("weekdays", INVALID);
        }
        EnumSet<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
        for (Integer number : numbers) {
            if (number == null || number < 1 || number > 7 || !days.add(DayOfWeek.of(number))) {
                throw ApiException.validation("weekdays", INVALID);
            }
        }
        return new Weekdays(days);
    }

    /** DB 값. 형식이 깨졌으면 데이터 불변식 위반 */
    static Weekdays fromBits(String bits) {
        if (bits == null || !bits.matches("[01]{7}") || !bits.contains("1")) {
            throw new IllegalStateException("class.weekdays 형식이 아니다: " + bits);
        }
        EnumSet<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
        for (int i = 0; i < 7; i++) {
            if (bits.charAt(i) == '1') {
                days.add(DayOfWeek.of(i + 1));
            }
        }
        return new Weekdays(days);
    }

    String bits() {
        StringBuilder bits = new StringBuilder(7);
        for (DayOfWeek day : DayOfWeek.values()) {
            bits.append(days.contains(day) ? '1' : '0');
        }
        return bits.toString();
    }

    /** 1(월)~7(일), 오름차순 */
    List<Integer> numbers() {
        return days.stream().map(DayOfWeek::getValue).sorted().toList();
    }

    boolean includes(DayOfWeek day) {
        return days.contains(day);
    }
}
