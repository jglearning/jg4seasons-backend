package com.jglearning.jg4seasons.clazz.api;

import java.util.Optional;

/** 반 변경. 빈 칸(null 포함)은 그대로 둔다. 권·개강일·요일은 바꿀 수 없다(새 반). */
public record ClassChange(Optional<String> name, Optional<ClassStatus> status, Optional<Boolean> allowFutureLessons) {

    public ClassChange {
        name = name == null ? Optional.empty() : name;
        status = status == null ? Optional.empty() : status;
        allowFutureLessons = allowFutureLessons == null ? Optional.empty() : allowFutureLessons;
    }

    public static ClassChange rename(String name) {
        return new ClassChange(Optional.of(name), Optional.empty(), Optional.empty());
    }

    public static ClassChange status(ClassStatus status) {
        return new ClassChange(Optional.empty(), Optional.of(status), Optional.empty());
    }
}
