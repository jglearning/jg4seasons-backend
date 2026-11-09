package com.jglearning.jg4seasons.listening.api;

import com.jglearning.jg4seasons.clazz.api.ClassLesson;
import com.jglearning.jg4seasons.clazz.api.TodayLesson;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 반 진도표와 학생 진도로 회차 상태를 정한다(STU-01·02, ADR-24). 오늘 회차는 clazz.api.TodayLesson(수업일이 지난 회차 중 가장 늦은 것). 밀림 = 그보다 앞선 reached 회차 중 미완료. 상태 우선순위 completed → today → overdue → open →
 * locked. 휴강 행만 있는 회차는 reached·open이 아니라 locked(2.3·flush의 423과 같음).
 */
public final class LessonBoard {

    public enum LessonState {
        COMPLETED,
        TODAY,
        OVERDUE,
        OPEN,
        LOCKED;

        public String value() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** 밀린 회차의 종류: 시작 안 함(미참여) · 듣다 멈춤 */
    public enum OverdueKind {
        NOT_STARTED,
        STOPPED;

        public String value() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** overdueKind는 OVERDUE일 때만, 아니면 null */
    public record Entry(ClassLesson lesson, LessonState state, OverdueKind overdueKind) {
        public Entry {
            Objects.requireNonNull(lesson, "lesson");
            Objects.requireNonNull(state, "state");
            if ((state == LessonState.OVERDUE) != (overdueKind != null)) {
                throw new IllegalArgumentException("overdueKind는 OVERDUE일 때만: " + state + " " + overdueKind);
            }
        }
    }

    /** entries는 seq 순, overdue는 오래된 순(수업일, seq) */
    public record Board(
            List<Entry> entries,
            Optional<ClassLesson> today,
            List<Entry> overdue,
            Optional<ClassLesson> next,
            int completedCount) {
        public Board {
            entries = List.copyOf(entries);
            overdue = List.copyOf(overdue);
        }
    }

    private static final Comparator<ClassLesson> BY_DATE_THEN_SEQ = TodayLesson.BY_DATE_THEN_SEQ;

    private LessonBoard() {}

    /** progress에 없는 회차는 시작 전. today는 학원 타임존 날짜 */
    public static Board evaluate(List<ClassLesson> lessons, Map<Long, LessonProgressStatus> progress, LocalDate today) {
        Optional<ClassLesson> todayLesson = TodayLesson.of(lessons);
        Optional<ClassLesson> next = lessons.stream()
                .filter(ClassLesson::held)
                .filter(lesson -> lesson.scheduledDate().isAfter(today))
                .min(BY_DATE_THEN_SEQ);
        List<Entry> entries = lessons.stream()
                .sorted(Comparator.comparingInt(ClassLesson::seq))
                .map(lesson -> entry(lesson, progress.get(lesson.lessonId()), todayLesson))
                .toList();
        List<Entry> overdue = entries.stream()
                .filter(entry -> entry.state() == LessonState.OVERDUE)
                .sorted(Comparator.comparing(Entry::lesson, BY_DATE_THEN_SEQ))
                .toList();
        int completed = (int) entries.stream()
                .filter(entry -> entry.state() == LessonState.COMPLETED)
                .count();
        return new Board(entries, todayLesson, overdue, next, completed);
    }

    private static Entry entry(ClassLesson lesson, LessonProgressStatus status, Optional<ClassLesson> today) {
        if (status == LessonProgressStatus.COMPLETED) {
            return new Entry(lesson, LessonState.COMPLETED, null);
        }
        if (today.isPresent() && today.get().lessonId() == lesson.lessonId()) {
            return new Entry(lesson, LessonState.TODAY, null);
        }
        if (lesson.reached()) { // reached가 있으면 오늘 회차도 있다 — 오늘 회차가 아닌 reached는 그보다 앞선 회차
            OverdueKind kind =
                    status == LessonProgressStatus.IN_PROGRESS ? OverdueKind.STOPPED : OverdueKind.NOT_STARTED;
            return new Entry(lesson, LessonState.OVERDUE, kind);
        }
        return new Entry(lesson, lesson.open() ? LessonState.OPEN : LessonState.LOCKED, null);
    }
}
