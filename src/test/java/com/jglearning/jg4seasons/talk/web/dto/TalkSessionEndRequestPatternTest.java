package com.jglearning.jg4seasons.talk.web.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.jglearning.jg4seasons.talk.TalkEndReason;
import com.jglearning.jg4seasons.talk.TalkRole;
import jakarta.validation.constraints.Pattern;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** 종료 요청 @Pattern 값 = enum code(#77 리뷰 9). 어긋나면 enum에만 있는 값은 400, 정규식에만 있는 값은 500 */
class TalkSessionEndRequestPatternTest {

    @Test
    void endReason_패턴은_TalkEndReason_code와_같다() throws Exception {
        assertThat(alternatives(TalkSessionEndRequest.class, "endReason"))
                .containsExactlyInAnyOrderElementsOf(Arrays.stream(TalkEndReason.values())
                        .map(TalkEndReason::code)
                        .toList());
    }

    @Test
    void role_패턴은_TalkRole_code와_같다() throws Exception {
        assertThat(alternatives(TalkSessionEndRequest.Turn.class, "role"))
                .containsExactlyInAnyOrderElementsOf(
                        Arrays.stream(TalkRole.values()).map(TalkRole::code).toList());
    }

    /** "^(a|b)$" → [a, b] */
    private static List<String> alternatives(Class<?> type, String field) throws Exception {
        String regexp =
                type.getDeclaredField(field).getAnnotation(Pattern.class).regexp();
        assertThat(regexp).startsWith("^(").endsWith(")$");
        return Stream.of(regexp.substring(2, regexp.length() - 2).split("\\|")).toList();
    }
}
