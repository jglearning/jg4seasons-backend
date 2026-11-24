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
    void 재시도는_전이가_아니다_grading에서_grading으로는_옮기지_않는다() {
        // 워커는 PENDING일 때만 GRADING으로 옮기고, 재시도 동안 제출은 grading에 그대로 둔다
        assertThat(SubmissionStatus.GRADING.canMoveTo(SubmissionStatus.GRADING)).isFalse();
        assertThat(SubmissionStatus.GRADING.canMoveTo(SubmissionStatus.PENDING)).isFalse();
    }

    @Test
    void 결과_사유_영역도_DB_값으로_왕복한다() {
        assertThat(AnswerResult.CORRECT.dbValue()).isEqualTo("correct");
        assertThat(UnsureReason.READ_MISMATCH.dbValue()).isEqualTo("read_mismatch");
        assertThat(Section.B.dbValue()).isEqualTo("B");
        for (AnswerResult result : AnswerResult.values()) {
            assertThat(AnswerResult.fromDb(result.dbValue())).isEqualTo(result);
        }
        for (UnsureReason reason : UnsureReason.values()) {
            assertThat(UnsureReason.fromDb(reason.dbValue())).isEqualTo(reason);
        }
        for (Section section : Section.values()) {
            assertThat(Section.fromDb(section.dbValue())).isEqualTo(section);
        }
        assertThatThrownBy(() -> AnswerResult.fromDb("CORRECT")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> UnsureReason.fromDb("guess")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Section.fromDb("b")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 판정은_결과가_있어야_하고_읽을_수_없음은_unclear와만() {
        assertThatThrownBy(() -> new Judgement(null, null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Judgement(AnswerResult.CORRECT, UnsureReason.UNREADABLE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Judgement(AnswerResult.BLANK, UnsureReason.UNREADABLE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Judgement(AnswerResult.WRONG, UnsureReason.UNREADABLE))
                .isInstanceOf(IllegalArgumentException.class);
        // unclear는 이유 없이 만들 수 없다
        assertThatThrownBy(() -> new Judgement(AnswerResult.UNCLEAR, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(new Judgement(AnswerResult.UNCLEAR, UnsureReason.UNREADABLE).result())
                .isEqualTo(AnswerResult.UNCLEAR);
    }
}
