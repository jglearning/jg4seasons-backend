package com.jglearning.jg4seasons.clazz.api;

import java.time.LocalDate;
import java.util.Objects;

/**
 * 반 진도표의 회차 하나(휴강 시프트로 행이 여럿이면 접은 값). seq·scheduledDate는 휴강이 아닌 행 중 seq가 가장 작은 것(없으면 휴강 행). reached = 휴강이
 * 아닌 행의 수업일이 오늘(학원 타임존) 이하 — "오늘 회차"·밀린 수업은 이것으로 고른다. open = 학생이 들을 수 있음(미래 회차 허용 반은 reached가 아니어도 open).
 * held = 휴강이 아닌 행이 있음(false면 휴강 행만 — 다음 수업이 될 수 없다).
 */
public record ClassLesson(
        long lessonId, int seq, LocalDate scheduledDate, boolean reached, boolean open, boolean held) {

    /** reached → open·held, open → held. 어긋나면 진도표 접기 버그 — LessonBoard가 이 규칙을 믿고 판정한다 */
    public ClassLesson {
        Objects.requireNonNull(scheduledDate, "scheduledDate");
        if (reached && !open) {
            throw new IllegalArgumentException("도달한 회차는 열려 있어야 한다: lesson " + lessonId);
        }
        if (open && !held) {
            throw new IllegalArgumentException("열린 회차는 휴강이 아닌 수업이 있어야 한다: lesson " + lessonId);
        }
    }
}
