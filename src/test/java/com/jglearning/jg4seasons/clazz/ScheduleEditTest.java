package com.jglearning.jg4seasons.clazz;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jglearning.jg4seasons.clazz.ScheduleEdit.Row;
import com.jglearning.jg4seasons.clazz.api.SessionStatus;
import com.jglearning.jg4seasons.infra.web.error.ApiException;
import com.jglearning.jg4seasons.infra.web.error.CommonErrorCode;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * 진도표 편집 규칙(ADR 0030). 수업 요일 화·목, 오늘 = 2026-11-10(화). 기본 진도표: L1 11-10 · L2 11-12 · L3 11-17 · L4 11-19(모두 수업, id 1~4).
 * 결과 목록 순서 = 새 seq.
 */
class ScheduleEditTest {

    static final Weekdays TUE_THU = Weekdays.fromNumbers(List.of(2, 4));
    static final LocalDate TODAY = d(10);

    static LocalDate d(int day) {
        return LocalDate.of(2026, 11, day);
    }

    static Row held(long id, long lesson, int day) {
        return new Row(id, lesson, d(day), SessionStatus.SCHEDULED, null);
    }

    static Row cancelled(long id, long lesson, int day, String note) {
        return new Row(id, lesson, d(day), SessionStatus.CANCELLED, note);
    }

    static final List<Row> BASE = List.of(held(1, 1, 10), held(2, 2, 12), held(3, 3, 17), held(4, 4, 19));

    private static void assertApiError(Runnable call, Object code, String field) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(ApiException.class, e -> {
            assertThat(e.errorCode()).isEqualTo(code);
            if (field != null) {
                assertThat(e.field()).isEqualTo(field);
            }
        });
    }

    // 휴강

    @Test
    void 휴강하고_뒤로_밀면_같은_회차가_다음_수업일에_새_행으로() {
        List<Row> result = ScheduleEdit.cancel(BASE, 2, "내신", true, TUE_THU, TODAY);

        assertThat(result)
                .containsExactly(
                        held(1, 1, 10),
                        cancelled(2, 2, 12, "내신"),
                        new Row(null, 2, d(17), SessionStatus.SCHEDULED, null),
                        held(3, 3, 19),
                        held(4, 4, 24));
    }

    @Test
    void 밀지_않으면_그_행만_휴강() {
        List<Row> result = ScheduleEdit.cancel(BASE, 2, null, false, TUE_THU, TODAY);

        assertThat(result).containsExactly(held(1, 1, 10), cancelled(2, 2, 12, null), held(3, 3, 17), held(4, 4, 19));
    }

    @Test
    void 마지막_행_휴강은_끝에_다음_수업일_행() {
        List<Row> result = ScheduleEdit.cancel(BASE, 4, null, true, TUE_THU, TODAY);

        assertThat(result)
                .containsExactly(
                        held(1, 1, 10),
                        held(2, 2, 12),
                        held(3, 3, 17),
                        cancelled(4, 4, 19, null),
                        new Row(null, 4, d(24), SessionStatus.SCHEDULED, null));
    }

    @Test
    void 이미_휴강한_날짜가_사이에_있으면_수업_행_날짜만_받는다() {
        // 11-17은 예전에 휴강(L3을 11-19로 밀었음)
        List<Row> rows =
                List.of(held(1, 1, 10), held(2, 2, 12), cancelled(3, 3, 17, "체험"), held(5, 3, 19), held(4, 4, 24));

        List<Row> result = ScheduleEdit.cancel(rows, 2, null, true, TUE_THU, TODAY);

        assertThat(result)
                .containsExactly(
                        held(1, 1, 10),
                        cancelled(2, 2, 12, null),
                        cancelled(3, 3, 17, "체험"),
                        new Row(null, 2, d(19), SessionStatus.SCHEDULED, null),
                        held(5, 3, 24),
                        held(4, 4, 26));
    }

    @Test
    void 다음_수업일은_수업_요일이면서_휴강_날짜가_아닌_날() {
        assertThat(ScheduleEdit.nextClassDay(d(12), TUE_THU, Set.of(d(17)))).isEqualTo(d(19));
        assertThat(ScheduleEdit.nextClassDay(d(19), TUE_THU, Set.of())).isEqualTo(d(24));
        assertThat(ScheduleEdit.nextClassDay(d(14), TUE_THU, Set.of()))
                .as("토요일 다음")
                .isEqualTo(d(17));
    }

    @Test
    void 마지막_회차의_다음_수업일도_휴강_날짜를_건너뛴다() {
        // 끝 뒤에 휴강 행만 남은 날(11-24)이 있으면 그날은 수업일이 아니다
        List<Row> rows = List.of(held(1, 1, 10), held(2, 2, 19), cancelled(3, 3, 24, null));

        List<Row> result = ScheduleEdit.cancel(rows, 2, null, true, TUE_THU, TODAY);

        assertThat(result)
                .containsExactly(
                        held(1, 1, 10),
                        cancelled(2, 2, 19, null),
                        cancelled(3, 3, 24, null),
                        new Row(null, 2, d(26), SessionStatus.SCHEDULED, null));
    }

    @Test
    void 이미_휴강한_행을_다시_휴강하면_그대로() {
        List<Row> rows = List.of(held(1, 1, 10), cancelled(2, 2, 12, "내신"), held(3, 2, 17));

        assertThat(ScheduleEdit.cancel(rows, 2, "다른 메모", true, TUE_THU, TODAY)).isEqualTo(rows);
    }

    @Test
    void 오늘_행은_휴강할_수_있고_지난_행은_409() {
        assertThat(ScheduleEdit.cancel(BASE, 1, null, false, TUE_THU, TODAY)
                        .get(0)
                        .status())
                .isEqualTo(SessionStatus.CANCELLED);
        assertApiError(
                () -> ScheduleEdit.cancel(BASE, 1, null, false, TUE_THU, d(12)), ClassErrorCode.SESSION_PAST, null);
    }

    @Test
    void 없는_행은_404() {
        assertApiError(() -> ScheduleEdit.cancel(BASE, 5, null, true, TUE_THU, TODAY), CommonErrorCode.NOT_FOUND, null);
        assertApiError(
                () -> ScheduleEdit.reschedule(BASE, 0, d(18), null, false, TUE_THU, TODAY),
                CommonErrorCode.NOT_FOUND,
                null);
    }

    @Test
    void 메모_없이_휴강하면_원래_메모를_유지하고_빈_메모는_지운다() {
        List<Row> rows = List.of(held(1, 1, 10), new Row(2L, 2, d(12), SessionStatus.SCHEDULED, "숙제"));

        assertThat(ScheduleEdit.cancel(rows, 2, null, false, TUE_THU, TODAY).get(1))
                .isEqualTo(cancelled(2, 2, 12, "숙제"));
        assertThat(ScheduleEdit.cancel(rows, 2, "", false, TUE_THU, TODAY).get(1))
                .as("4.6과 같은 뜻 — 빈 메모 = 지움(null), 빈 문자열로 저장하지 않는다")
                .isEqualTo(cancelled(2, 2, 12, null));
    }

    @Test
    void 행은_빈_메모를_null로_맞추고_날짜_상태가_없으면_만들_수_없다() {
        assertThat(new Row(1L, 1, d(10), SessionStatus.SCHEDULED, "").note()).isNull();
        assertThatThrownBy(() -> new Row(1L, 1, null, SessionStatus.SCHEDULED, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Row(1L, 1, d(10), null, null)).isInstanceOf(NullPointerException.class);
    }

    // 날짜 변경

    @Test
    void 날짜_변경_밀지_않으면_앞뒤_수업_사이만() {
        assertThat(ScheduleEdit.reschedule(BASE, 3, d(18), null, false, TUE_THU, TODAY))
                .containsExactly(held(1, 1, 10), held(2, 2, 12), held(3, 3, 18), held(4, 4, 19));

        assertApiError(
                () -> ScheduleEdit.reschedule(BASE, 3, d(12), null, false, TUE_THU, TODAY),
                CommonErrorCode.VALIDATION,
                "scheduledDate");
        assertApiError(
                () -> ScheduleEdit.reschedule(BASE, 3, d(19), null, false, TUE_THU, TODAY),
                CommonErrorCode.VALIDATION,
                "scheduledDate");
        assertApiError(
                () -> ScheduleEdit.reschedule(BASE, 2, d(9), null, false, TUE_THU, TODAY),
                CommonErrorCode.VALIDATION,
                "scheduledDate");
    }

    @Test
    void 같은_날짜를_보내면_날짜_변경이_아니다() {
        // 수정 폼이 원래 날짜와 메모를 함께 보내도 지난 행·휴강 행의 메모가 바뀐다(ADR 0030 — 메모는 언제든). 밀기도 무시
        List<Row> rows = List.of(held(1, 1, 5), cancelled(2, 2, 10, null), held(3, 2, 12), held(4, 3, 17));

        assertThat(ScheduleEdit.reschedule(rows, 1, d(5), "결석 많음", true, TUE_THU, TODAY))
                .as("지난 행")
                .containsExactly(
                        new Row(1L, 1, d(5), SessionStatus.SCHEDULED, "결석 많음"),
                        cancelled(2, 2, 10, null),
                        held(3, 2, 12),
                        held(4, 3, 17));
        assertThat(ScheduleEdit.reschedule(rows, 2, d(10), "체험학습", false, TUE_THU, TODAY)
                        .get(1))
                .as("휴강 행")
                .isEqualTo(cancelled(2, 2, 10, "체험학습"));
        assertThat(ScheduleEdit.reschedule(rows, 3, d(12), null, true, TUE_THU, TODAY))
                .as("수업 행 — 같은 날짜 + 밀기는 아무것도 안 바뀜")
                .isEqualTo(rows);
    }

    @Test
    void 날짜_변경_뒤로_밀면_뒤_수업들을_새_날짜의_다음_수업일부터() {
        assertThat(ScheduleEdit.reschedule(BASE, 2, d(14), null, true, TUE_THU, TODAY))
                .as("토요일 보강으로 옮기면 뒤는 화·목")
                .containsExactly(held(1, 1, 10), held(2, 2, 14), held(3, 3, 17), held(4, 4, 19));
        assertThat(ScheduleEdit.reschedule(BASE, 2, d(17), null, true, TUE_THU, TODAY))
                .containsExactly(held(1, 1, 10), held(2, 2, 17), held(3, 3, 19), held(4, 4, 24));
    }

    @Test
    void 날짜를_밀_때도_휴강_행은_그대로_두고_그_날짜를_건너뛴다() {
        List<Row> rows = List.of(held(1, 1, 10), held(2, 2, 12), cancelled(3, 3, 17, null), held(5, 3, 19));

        assertThat(ScheduleEdit.reschedule(rows, 2, d(13), null, true, TUE_THU, TODAY))
                .containsExactly(held(1, 1, 10), held(2, 2, 13), cancelled(3, 3, 17, null), held(5, 3, 19));
    }

    @Test
    void 휴강_행은_날짜를_못_바꾸고_메모는_바꾼다() {
        List<Row> rows = List.of(held(1, 1, 10), cancelled(2, 2, 12, null), held(3, 2, 17));

        assertApiError(
                () -> ScheduleEdit.reschedule(rows, 2, d(13), null, false, TUE_THU, TODAY),
                CommonErrorCode.VALIDATION,
                "scheduledDate");
        assertThat(ScheduleEdit.reschedule(rows, 2, null, "보강 예정", false, TUE_THU, TODAY)
                        .get(1))
                .isEqualTo(cancelled(2, 2, 12, "보강 예정"));
    }

    @Test
    void 지난_행은_메모만_바꾸고_빈_메모는_지운다() {
        List<Row> rows = List.of(new Row(1L, 1, d(10), SessionStatus.SCHEDULED, "숙제 많음"), held(2, 2, 12));

        assertApiError(
                () -> ScheduleEdit.reschedule(rows, 1, d(11), null, false, TUE_THU, d(12)),
                ClassErrorCode.SESSION_PAST,
                null);
        assertThat(ScheduleEdit.reschedule(rows, 1, null, "", false, TUE_THU, d(12))
                        .get(0)
                        .note())
                .isNull();
        assertThat(ScheduleEdit.reschedule(rows, 1, null, "복습", false, TUE_THU, d(12))
                        .get(0)
                        .note())
                .isEqualTo("복습");
    }

    @Test
    void 같은_날은_휴강_행이_먼저() {
        // 11-12 행을 휴강하고 밀면 새 행은 11-17. 11-17에 예전 휴강 행이 있으면 그 휴강 행이 먼저
        List<Row> rows = List.of(held(1, 1, 12), cancelled(2, 9, 17, null), held(3, 2, 19));

        assertThat(ScheduleEdit.reschedule(rows, 1, d(17), null, false, TUE_THU, TODAY))
                .containsExactly(cancelled(2, 9, 17, null), held(1, 1, 17), held(3, 2, 19));
    }
}
