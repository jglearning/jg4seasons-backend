package com.jglearning.jg4seasons.clazz;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jglearning.jg4seasons.infra.web.error.ApiException;
import com.jglearning.jg4seasons.infra.web.error.CommonErrorCode;
import java.time.DayOfWeek;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** 수업 요일. API는 1(월)~7(일) 숫자 배열, DB는 월~일 7자 비트 문자열. */
class WeekdaysTest {

    @Test
    void 숫자와_비트는_서로_바뀐다() {
        assertThat(Weekdays.fromNumbers(List.of(1, 3)).bits()).isEqualTo("1010000");
        assertThat(Weekdays.fromNumbers(List.of(7)).bits()).isEqualTo("0000001");
        assertThat(Weekdays.fromBits("1010000").numbers()).containsExactly(1, 3);
        assertThat(Weekdays.fromBits("0000001").includes(DayOfWeek.SUNDAY)).isTrue();
        assertThat(Weekdays.fromBits("0000001").includes(DayOfWeek.MONDAY)).isFalse();
    }

    @Test
    void 순서가_달라도_오름차순() {
        assertThat(Weekdays.fromNumbers(List.of(5, 2)).numbers()).containsExactly(2, 5);
    }

    @Test
    void 범위_밖_중복_빈_목록은_weekdays_400() {
        List<List<Integer>> invalid = List.of(List.of(0), List.of(8), List.of(1, 1), List.of());
        for (List<Integer> numbers : invalid) {
            assertValidation(numbers);
        }
        assertValidation(null);
        assertValidation(Arrays.asList(1, null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"000000", "0000000", "01020000", "1010002", ""})
    void DB_비트가_깨졌으면_IllegalState(String bits) {
        assertThatThrownBy(() -> Weekdays.fromBits(bits)).isInstanceOf(IllegalStateException.class);
    }

    private static void assertValidation(List<Integer> numbers) {
        assertThatThrownBy(() -> Weekdays.fromNumbers(numbers))
                .as("%s", numbers)
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.errorCode()).isEqualTo(CommonErrorCode.VALIDATION);
                    assertThat(e.field()).isEqualTo("weekdays");
                });
    }
}
