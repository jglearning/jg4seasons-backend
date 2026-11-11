package com.jglearning.jg4seasons.clazz;

import com.jglearning.jg4seasons.clazz.api.SessionStatus;
import java.time.LocalDate;

/**
 * 진도표 행의 응답 상태(4.2·4.5~4.7, 이슈 #45). done은 DB에 쓰지 않고 응답 때 날짜로 정한다: 휴강이면 cancelled, 수업 날짜가 오늘(학원 날짜) 이전이면 done,
 * 그 밖은 scheduled. 오늘 수업은 scheduled — 지난 행 편집 거절(SESSION_PAST)과 같은 기준. 저장된 done은 보지 않는다(날짜만).
 */
final class SessionStatusRule {

    private SessionStatusRule() {}

    static SessionStatus shown(SessionStatus stored, LocalDate scheduledDate, LocalDate today) {
        if (stored == SessionStatus.CANCELLED) {
            return SessionStatus.CANCELLED;
        }
        return scheduledDate.isBefore(today) ? SessionStatus.DONE : SessionStatus.SCHEDULED;
    }
}
