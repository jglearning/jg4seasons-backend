package com.jglearning.jg4seasons.listening;

import static org.assertj.core.api.Assertions.assertThat;

import com.jglearning.jg4seasons.clazz.api.ClassLesson;
import com.jglearning.jg4seasons.listening.LessonBoard.Board;
import com.jglearning.jg4seasons.listening.LessonBoard.Entry;
import com.jglearning.jg4seasons.listening.LessonBoard.LessonState;
import com.jglearning.jg4seasons.listening.LessonBoard.OverdueKind;
import com.jglearning.jg4seasons.listening.api.LessonProgressStatus;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 회차 상태 판정(ADR-24): 오늘 회차 = 수업일이 지난(reached) 회차 중 가장 늦은 것, 밀림 = 그보다 앞선 reached 회차 중 미완료. 상태 우선순위 completed → today
 * → overdue → open → locked. 회차 id = 회차 번호, 수업일 = 11/1 + (번호 - 1)일, 오늘 = 4회차 수업일.
 */
class LessonBoardTest {

    static final LocalDate DAY1 = LocalDate.of(2026, 11, 1);
    static final LocalDate TODAY = DAY1.plusDays(3); // 4회차 수업일

    /** n회차: 수업일 DAY1 + (n-1), reached = 수업일 <= today, open = reached 또는 미래 허용 */
    static List<ClassLesson> lessons(int count, LocalDate today, boolean allowFuture) {
        List<ClassLesson> lessons = new ArrayList<>();
        for (int n = 1; n <= count; n++) {
            LocalDate date = DAY1.plusDays(n - 1);
            boolean reached = !date.isAfter(today);
            lessons.add(new ClassLesson(n, n, date, reached, reached || allowFuture, true));
        }
        return lessons;
    }

    static Board board(List<ClassLesson> lessons, Map<Long, LessonProgressStatus> progress) {
        return LessonBoard.evaluate(lessons, progress, TODAY);
    }

    static LessonState state(Board board, long lessonId) {
        return board.entries().stream()
                .filter(entry -> entry.lesson().lessonId() == lessonId)
                .findFirst()
                .orElseThrow()
                .state();
    }

    @Test
    void 오늘_회차는_도달한_것_중_가장_늦은_것() {
        Board board = board(lessons(6, TODAY, false), Map.of());

        assertThat(board.today()).map(ClassLesson::lessonId).contains(4L);
        assertThat(state(board, 4)).isEqualTo(LessonState.TODAY);
        assertThat(state(board, 5)).isEqualTo(LessonState.LOCKED);
    }

    @Test
    void 앞선_미완료는_밀림_오래된_순() {
        Board board = board(lessons(6, TODAY, false), Map.of(2L, LessonProgressStatus.COMPLETED));

        assertThat(board.overdue())
                .extracting(entry -> entry.lesson().lessonId())
                .containsExactly(1L, 3L);
        assertThat(state(board, 2)).isEqualTo(LessonState.COMPLETED);
    }

    @Test
    void 밀림_종류는_진도로() {
        Board board = board(
                lessons(4, TODAY, false),
                Map.of(1L, LessonProgressStatus.IN_PROGRESS, 2L, LessonProgressStatus.NOT_STARTED));

        assertThat(board.overdue())
                .extracting(Entry::overdueKind)
                .containsExactly(OverdueKind.STOPPED, OverdueKind.NOT_STARTED, OverdueKind.NOT_STARTED);
    }

    @Test
    void 완료는_날짜와_무관하게_completed() {
        Board board = board(lessons(6, TODAY, false), Map.of(4L, LessonProgressStatus.COMPLETED));

        assertThat(state(board, 4)).isEqualTo(LessonState.COMPLETED);
        assertThat(board.today()).as("오늘 회차는 그대로").map(ClassLesson::lessonId).contains(4L);
        assertThat(board.completedCount()).isEqualTo(1);
    }

    @Test
    void 미래_허용이면_수업일_전_회차는_open이고_오늘은_수업일_기준() {
        Board board = board(lessons(24, TODAY, true), Map.of());

        assertThat(board.today()).map(ClassLesson::lessonId).contains(4L);
        assertThat(state(board, 5)).isEqualTo(LessonState.OPEN);
        assertThat(state(board, 24)).isEqualTo(LessonState.OPEN);
        assertThat(board.overdue()).hasSize(3);
    }

    @Test
    void 휴강만_있는_회차는_locked이고_밀림이_아니다() {
        List<ClassLesson> lessons = new ArrayList<>(lessons(4, TODAY, false));
        lessons.set(1, new ClassLesson(2, 2, DAY1.plusDays(1), false, false, false));

        Board board = board(lessons, Map.of());

        assertThat(state(board, 2)).isEqualTo(LessonState.LOCKED);
        assertThat(board.overdue())
                .extracting(entry -> entry.lesson().lessonId())
                .containsExactly(1L, 3L);
    }

    @Test
    void 다음_수업은_수업일이_오늘_뒤인_가장_이른_회차() {
        List<ClassLesson> lessons = new ArrayList<>(lessons(6, TODAY, false));
        lessons.set(4, new ClassLesson(5, 5, DAY1.plusDays(4), false, false, false)); // 5회차는 휴강만

        Board board = board(lessons, Map.of());

        assertThat(board.next()).map(ClassLesson::lessonId).contains(6L);
    }

    @Test
    void 개강_전은_today_없음_next_1회차() {
        Board board = LessonBoard.evaluate(lessons(24, DAY1.minusDays(1), false), Map.of(), DAY1.minusDays(1));

        assertThat(board.today()).isEmpty();
        assertThat(board.overdue()).isEmpty();
        assertThat(board.next()).map(ClassLesson::lessonId).contains(1L);
    }

    @Test
    void 종강_후는_today_24회차_next_없음() {
        LocalDate after = DAY1.plusDays(40);
        Board board = LessonBoard.evaluate(lessons(24, after, false), Map.of(), after);

        assertThat(board.today()).map(ClassLesson::lessonId).contains(24L);
        assertThat(board.next()).isEmpty();
        assertThat(board.overdue()).hasSize(23);
    }

    @Test
    void 상태_값은_소문자() {
        assertThat(LessonState.OVERDUE.value()).isEqualTo("overdue");
        assertThat(OverdueKind.NOT_STARTED.value()).isEqualTo("not_started");
        assertThat(OverdueKind.STOPPED.value()).isEqualTo("stopped");
    }
}
