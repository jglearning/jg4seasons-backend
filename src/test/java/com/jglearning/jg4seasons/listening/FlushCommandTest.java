package com.jglearning.jg4seasons.listening;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jglearning.jg4seasons.listening.FlushService.FlushCommand;
import com.jglearning.jg4seasons.listening.segment.SegmentFilter.IncomingSegment;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 서비스 입력: 필수 값은 null일 수 없다(컨트롤러를 거치지 않는 호출자도 같은 보장). 세그먼트 원소의 null은 버릴 대상이라 허용. */
class FlushCommandTest {

    static final Instant AT = Instant.parse("2026-11-10T03:00:00Z");
    static final String SESSION = "7c2e0000-0000-4000-8000-000000000001";

    @Test
    void 필수_값이_null이면_만들_수_없다() {
        assertThatThrownBy(() -> new FlushCommand(null, 1L, AT, null, List.of()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new FlushCommand(SESSION, 1L, null, null, List.of()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new FlushCommand(SESSION, 1L, AT, null, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void 세그먼트_원소의_null은_허용하고_복사한다() {
        List<IncomingSegment> segments = new ArrayList<>(Arrays.asList(new IncomingSegment(0, 1, AT), null));

        FlushCommand command = new FlushCommand(SESSION, 1L, AT, null, segments);
        segments.clear();

        assertThat(command.segments()).hasSize(2);
        assertThatThrownBy(() -> command.segments().add(null)).isInstanceOf(UnsupportedOperationException.class);
    }
}
