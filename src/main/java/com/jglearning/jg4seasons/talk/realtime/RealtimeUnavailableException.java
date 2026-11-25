package com.jglearning.jg4seasons.talk.realtime;

/** 모델 서비스에서 임시 키를 받지 못함(503 REALTIME_UNAVAILABLE). 메시지에 키·응답 본문을 넣지 않는다 */
public class RealtimeUnavailableException extends RuntimeException {

    public RealtimeUnavailableException(String message) {
        super(message);
    }
}
