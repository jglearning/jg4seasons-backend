package com.jglearning.jg4seasons.wordtest.reading;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jglearning.jg4seasons.wordtest.grading.Reading;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** LLM이 옮겨 적은 결과 검증: 번호 1~N이 정확히 한 번씩, 칸 길이(100자), 읽을 수 없음은 글자 없이 */
class ReadValidationTest {

    static ReadResult result(ReadItem... items) {
        return new ReadResult(List.of(items), "model-x", "v1", 10, 20);
    }

    @Test
    void 번호_순으로_정렬하고_그대로_옮긴다() {
        List<Reading> readings = ReadValidation.check(
                result(
                        new ReadItem(3, "receive", false),
                        new ReadItem(1, "beautful", false),
                        new ReadItem(2, "", false)),
                3);

        assertThat(readings)
                .containsExactly(new Reading("beautful", false), new Reading("", false), new Reading("receive", false));
    }

    @Test
    void 문항_수가_다르면_COUNT_MISMATCH() {
        assertFailure(
                () -> ReadValidation.check(result(new ReadItem(1, "a", false), new ReadItem(2, "b", false)), 3),
                ReadFailure.Kind.COUNT_MISMATCH);
    }

    @Test
    void 번호가_범위_밖이거나_겹치면_COUNT_MISMATCH() {
        assertFailure(
                () -> ReadValidation.check(result(new ReadItem(0, "a", false), new ReadItem(1, "b", false)), 2),
                ReadFailure.Kind.COUNT_MISMATCH);
        assertFailure(
                () -> ReadValidation.check(result(new ReadItem(1, "a", false), new ReadItem(3, "b", false)), 2),
                ReadFailure.Kind.COUNT_MISMATCH);
        assertFailure(
                () -> ReadValidation.check(result(new ReadItem(1, "a", false), new ReadItem(1, "b", false)), 2),
                ReadFailure.Kind.COUNT_MISMATCH);
    }

    @Test
    void 백_자를_넘는_칸은_읽을_수_없음이고_글자는_버린다() {
        List<Reading> readings = ReadValidation.check(
                result(new ReadItem(1, "a".repeat(100), false), new ReadItem(2, "a".repeat(101), false)), 2);

        assertThat(readings).containsExactly(new Reading("a".repeat(100), false), new Reading(null, true));
    }

    @Test
    void 읽을_수_없음이면_글자가_있어도_버리고_null_글자는_빈칸() {
        List<Reading> readings =
                ReadValidation.check(result(new ReadItem(1, "atm", true), new ReadItem(2, null, false)), 2);

        assertThat(readings).containsExactly(new Reading(null, true), new Reading(null, false));
    }

    @Test
    void 항목이_없거나_빈_항목이_있으면_MALFORMED() {
        assertFailure(() -> ReadValidation.check(new ReadResult(null, "m", "v1", 0, 0), 1), ReadFailure.Kind.MALFORMED);
        List<ReadItem> withNull = new ArrayList<>();
        withNull.add(null);
        assertFailure(
                () -> ReadValidation.check(new ReadResult(withNull, "m", "v1", 0, 0), 1), ReadFailure.Kind.MALFORMED);
    }

    @Test
    void 사진_문제는_재시도하지_않고_나머지는_재시도한다() {
        assertThat(ReadFailure.Kind.PHOTO_MISSING.retryable()).isFalse();
        assertThat(ReadFailure.Kind.PHOTO_TOO_LARGE.retryable()).isFalse();
        for (ReadFailure.Kind kind : List.of(
                ReadFailure.Kind.TIMEOUT,
                ReadFailure.Kind.REFUSED,
                ReadFailure.Kind.TRUNCATED,
                ReadFailure.Kind.MALFORMED,
                ReadFailure.Kind.COUNT_MISMATCH,
                ReadFailure.Kind.API_ERROR)) {
            assertThat(kind.retryable()).as(kind.name()).isTrue();
        }
    }

    private static void assertFailure(
            org.assertj.core.api.ThrowableAssert.ThrowingCallable call, ReadFailure.Kind kind) {
        assertThatThrownBy(call)
                .isInstanceOfSatisfying(
                        ReadFailure.class, failure -> assertThat(failure.kind()).isEqualTo(kind));
    }
}
