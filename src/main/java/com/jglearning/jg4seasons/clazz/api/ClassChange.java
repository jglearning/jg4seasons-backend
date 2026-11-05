package com.jglearning.jg4seasons.clazz.api;

import java.util.Optional;

/** 반 변경. 빈 칸은 그대로 둔다. 권·개강일·요일은 바꿀 수 없다(새 반). */
public record ClassChange(Optional<String> name, Optional<ClassStatus> status, Optional<Boolean> allowFutureLessons) {}
