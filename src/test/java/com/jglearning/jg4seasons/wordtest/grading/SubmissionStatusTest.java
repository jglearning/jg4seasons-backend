package com.jglearning.jg4seasons.wordtest.grading;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;
import org.junit.jupiter.api.Test;

/** 제출 상태 전이(기획 §5-4). 1단계 허용은 pending → grading → graded | failed 셋뿐, 나머지는 예외 */
class SubmissionStatusTest {

    static final Set<String> ALLOWED = Set.of("PENDING>GRADING", "GRADING>GRADED", "GRADING>FAILED");

    @Test
    void 허용_전이는_셋이고_나머지는_모두_막는다() {
        int allowed = 0;
        for (SubmissionStatus from : SubmissionStatus.values()) {
            for (SubmissionStatus to : SubmissionStatus.values()) {
                boolean expected = ALLOWED.contains(from + ">" + to);
                assertThat(from.canMoveTo(to)).as("%s → %s", from, to).isEqualTo(expected);
                if (expected) {
                    allowed++;
                }
            }
        }
        assertThat(allowed).isEqualTo(3);
    }

    @Test
    void 허용_전이는_다음_상태를_돌려준다() {
        assertThat(SubmissionStatus.PENDING.moveTo(SubmissionStatus.GRADING)).isEqualTo(SubmissionStatus.GRADING);
        assertThat(SubmissionStatus.GRADING.moveTo(SubmissionStatus.FAILED)).isEqualTo(SubmissionStatus.FAILED);
    }

    @Test
    void 금지_전이는_현재와_다음_값을_담아_예외() {
        assertThatThrownBy(() -> SubmissionStatus.GRADED.moveTo(SubmissionStatus.PENDING))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("graded")
                .hasMessageContaining("pending");
        assertThatThrownBy(() -> SubmissionStatus.GRADING.moveTo(SubmissionStatus.GRADING))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void DB_값은_소문자_이름이고_여섯_값_모두_왕복한다() {
        assertThat(SubmissionStatus.CONFIRMING.dbValue()).isEqualTo("confirming");
        for (SubmissionStatus status : SubmissionStatus.values()) {
            assertThat(SubmissionStatus.fromDb(status.dbValue())).isEqualTo(status);
        }
        assertThatThrownBy(() -> SubmissionStatus.fromDb("done")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SubmissionStatus.fromDb("GRADED")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 결과와_사유의_DB_값은_V12_CHECK와_같다() {
        assertThat(AnswerResult.CORRECT.dbValue()).isEqualTo("correct");
        assertThat(AnswerResult.BLANK.dbValue()).isEqualTo("blank");
        assertThat(UnsureReason.READ_MISMATCH.dbValue()).isEqualTo("read_mismatch");
        assertThat(UnsureReason.UNREADABLE.dbValue()).isEqualTo("unreadable");
        assertThat(Section.valueOf("B").name()).isEqualTo("B");
    }
}
