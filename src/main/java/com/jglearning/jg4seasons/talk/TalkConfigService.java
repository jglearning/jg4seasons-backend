package com.jglearning.jg4seasons.talk;

import com.jglearning.jg4seasons.content.api.LessonWord;
import com.jglearning.jg4seasons.talk.TalkAccess.TalkContext;
import com.jglearning.jg4seasons.talk.persistence.TalkSessionStore;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 설정 조회(계약 D-1 + 추가 지시 가-1). 기능이 꺼진 학원은 404, 구독이 없으면 entitled false(나머지는 그대로, 단어는 빈 목록). 사용 기간은 보지 않는다(세션 생성에서
 * 403)
 */
@Service
public class TalkConfigService {

    /** 화면에 그대로 내려 주는 값 */
    public record Config(
            boolean entitled,
            TalkLevel level,
            int maxSeconds,
            int remainingToday,
            List<TalkTopic> topics,
            List<LessonWord> lessonWords) {}

    private final TalkAccess access;
    private final TalkSessionStore store;
    private final TalkProperties properties;

    TalkConfigService(TalkAccess access, TalkSessionStore store, TalkProperties properties) {
        this.access = access;
        this.store = store;
        this.properties = properties;
    }

    public Config config(long academyId, long studentId) {
        access.requireEnabled(academyId);
        boolean entitled = access.entitled(academyId);
        TalkContext context = access.context(academyId, studentId);
        int used = store.countIssuedSince(
                studentId, access.startOfToday(academyId), properties.realtime().keyTtlSeconds());
        return new Config(
                entitled,
                context.level(),
                properties.maxSeconds(),
                Math.max(0, TalkSessionService.DAILY_LIMIT - used),
                List.of(TalkTopic.values()),
                entitled ? context.words() : List.of());
    }
}
