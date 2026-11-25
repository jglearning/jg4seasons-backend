package com.jglearning.jg4seasons.talk.realtime;

/**
 * 실시간 음성 모델의 임시 키 발급. 음성은 브라우저와 모델이 직접 주고받고, 서버는 지시문을 묶은 임시 키만 만든다. 구현은 설정(jg4seasons.talk.realtime.type)으로
 * 고른다: openai(실제) · fake(로컬·테스트) · none(꺼짐 — 발급하면 RealtimeUnavailableException)
 */
public interface RealtimeSessionIssuer {

    /** 실패(서비스 오류·인증 실패·응답 모양 다름·시간 초과)는 모두 RealtimeUnavailableException */
    IssuedSecret issue(RealtimeSessionRequest request);
}
