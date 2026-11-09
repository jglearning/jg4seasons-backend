package com.jglearning.jg4seasons.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.SecureRandom;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.random.RandomGenerator;
import org.junit.jupiter.api.Test;

/** 개인 코드: 숫자 4자리(0으로 시작 가능), 이미 있으면 다시 뽑고 20번 모두 겹치면 실패(결정 A). */
class PersonalCodeGeneratorTest {

    /** nextInt(bound)가 정해 둔 값을 차례로 돌려주는 난수 */
    static RandomGenerator fixed(Integer... values) {
        Deque<Integer> queue = new ArrayDeque<>(List.of(values));
        return new RandomGenerator() {
            @Override
            public long nextLong() {
                throw new UnsupportedOperationException();
            }

            @Override
            public int nextInt(int bound) {
                int value = queue.isEmpty() ? values[values.length - 1] : queue.poll();
                if (value < 0 || value >= bound) {
                    throw new IllegalArgumentException("범위 밖: " + value);
                }
                return value;
            }
        };
    }

    @Test
    void 숫자_4자리이고_0으로_시작할_수_있다() {
        assertThat(new PersonalCodeGenerator(fixed(482)).next(code -> false)).isEqualTo("0482");
        assertThat(new PersonalCodeGenerator(fixed(0)).next(code -> false)).isEqualTo("0000");
        assertThat(new PersonalCodeGenerator(fixed(9999)).next(code -> false)).isEqualTo("9999");
    }

    @Test
    void 실제_난수로도_형식이_같다() {
        PersonalCodeGenerator generator = new PersonalCodeGenerator(new SecureRandom());

        for (int i = 0; i < 1_000; i++) {
            assertThat(generator.next(code -> false)).matches("^\\d{4}$");
        }
    }

    @Test
    void 겹치면_다시_뽑는다() {
        List<String> asked = new ArrayList<>();

        String code = new PersonalCodeGenerator(fixed(1234, 5678)).next(candidate -> {
            asked.add(candidate);
            return candidate.equals("1234");
        });

        assertThat(code).isEqualTo("5678");
        assertThat(asked).containsExactly("1234", "5678");
    }

    @Test
    void 스무_번_모두_겹치면_IllegalStateException() {
        List<String> asked = new ArrayList<>();

        assertThatThrownBy(() -> new PersonalCodeGenerator(fixed(7)).next(candidate -> asked.add(candidate)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageNotContaining("0007");
        assertThat(asked).hasSize(PersonalCodeGenerator.MAX_TRIES);
        assertThat(PersonalCodeGenerator.MAX_TRIES).isEqualTo(20);
    }
}
