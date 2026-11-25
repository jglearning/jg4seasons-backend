package com.jglearning.jg4seasons.talk.web.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.jglearning.jg4seasons.talk.TalkTopic;
import jakarta.validation.constraints.Pattern;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * 요청 칸의 @Pattern 값 목록이 enum code와 같은지(#76 리뷰 2). 어긋나면 enum에만 있는 값은 400, @Pattern에만 있는 값은 컨트롤러에서 500이 된다
 */
class TalkSessionCreateRequestPatternTest {

    @Test
    void topic_패턴은_TalkTopic_code와_같다() throws Exception {
        assertThat(alternatives("topic"))
                .containsExactlyInAnyOrderElementsOf(
                        Arrays.stream(TalkTopic.values()).map(TalkTopic::code).toList());
    }

    /** "^(a|b|c)$" → [a, b, c] */
    private static List<String> alternatives(String component) throws Exception {
        Pattern pattern = TalkSessionCreateRequest.class
                .getRecordComponents()[index(component)]
                .getAccessor()
                .getDeclaringClass()
                .getDeclaredField(component)
                .getAnnotation(Pattern.class);
        String regexp = pattern.regexp();
        assertThat(regexp).startsWith("^(").endsWith(")$");
        return Stream.of(regexp.substring(2, regexp.length() - 2).split("\\|")).toList();
    }

    private static int index(String component) {
        var components = TalkSessionCreateRequest.class.getRecordComponents();
        for (int i = 0; i < components.length; i++) {
            if (components[i].getName().equals(component)) {
                return i;
            }
        }
        throw new IllegalArgumentException(component);
    }
}
