package com.jglearning.jg4seasons.clazz;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** 반 가입 코드: 8자, 대문자·숫자에서 혼동 문자(0·O·1·I·L) 제외. 이미 있는 코드면 다시 뽑는다. */
class AccessCodeGeneratorTest {

    @Test
    void 여덟_자이고_혼동_문자가_없다() {
        AccessCodeGenerator generator = new AccessCodeGenerator(new SecureRandom());

        for (int i = 0; i < 1_000; i++) {
            String code = generator.next(candidate -> false);
            assertThat(code).hasSize(8).matches("[A-Z2-9]{8}").doesNotContainAnyWhitespaces();
            assertThat(code).doesNotContain("0", "O", "1", "I", "L");
        }
        assertThat(AccessCodeGenerator.ALPHABET).hasSize(31);
    }

    @Test
    void 입장_요청_형식과_맞는다() {
        String code = new AccessCodeGenerator(new SecureRandom()).next(candidate -> false);

        // 학생 입장 요청의 가입 코드 형식(대문자 변환 뒤)
        assertThat(code).matches("^[A-Z0-9]{8}$");
    }

    @Test
    void 있는_코드면_다시_뽑는다() {
        List<String> asked = new ArrayList<>();
        AccessCodeGenerator generator = new AccessCodeGenerator(new Random(42));

        String code = generator.next(candidate -> {
            asked.add(candidate);
            return asked.size() < 3;
        });

        assertThat(asked).hasSize(3);
        assertThat(code).isEqualTo(asked.get(2));
    }

    @Test
    void 다섯_번_모두_겹치면_예외() {
        List<String> asked = new ArrayList<>();
        AccessCodeGenerator generator = new AccessCodeGenerator(new Random(42));

        assertThatThrownBy(() -> generator.next(candidate -> asked.add(candidate)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(asked).hasSize(AccessCodeGenerator.MAX_TRIES).hasSize(5);
    }
}
