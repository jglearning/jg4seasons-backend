package com.jglearning.jg4seasons.wordtest.grading;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 제출 상태(기획 §5-4). DB 값은 소문자 이름(V12 CHECK). 여섯 값을 모두 두지만 1단계 전이는 pending → grading → graded | failed뿐이다. confirming(학생 확인)·
 * rejected(다시 찍기)로 가는 전이는 2단계에서 {@link #NEXT}에 더한다.
 *
 * <p>재시도는 전이가 아니다: 읽기에 실패하면 작업(grading_job)만 다시 잡고 제출은 grading에 그대로 둔다. 워커는 제출이 PENDING일 때만 GRADING으로 옮기고, 이미
 * GRADING이면 옮기지 않고 이어서 처리한다(같은 상태로의 전이는 금지).
 */
public enum SubmissionStatus {
    PENDING,
    GRADING,
    CONFIRMING,
    GRADED,
    REJECTED,
    FAILED;

    private static final Map<SubmissionStatus, Set<SubmissionStatus>> NEXT =
            Map.of(PENDING, Set.of(GRADING), GRADING, Set.of(GRADED, FAILED));

    public String dbValue() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** 소문자 DB 값만 받는다. 모르는 값은 IllegalArgumentException */
    public static SubmissionStatus fromDb(String value) {
        for (SubmissionStatus status : values()) {
            if (status.dbValue().equals(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("모르는 제출 상태: " + value);
    }

    public boolean canMoveTo(SubmissionStatus next) {
        return NEXT.getOrDefault(this, Set.of()).contains(next);
    }

    /** 허용된 전이면 next, 아니면 IllegalStateException(상태 변경은 이 검사를 거친 한 곳에서만) */
    public SubmissionStatus moveTo(SubmissionStatus next) {
        if (!canMoveTo(next)) {
            throw new IllegalStateException("허용하지 않는 제출 상태 전이: " + dbValue() + " → " + next.dbValue());
        }
        return next;
    }
}
