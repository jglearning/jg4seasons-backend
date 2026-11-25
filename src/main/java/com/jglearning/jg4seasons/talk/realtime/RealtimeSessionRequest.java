package com.jglearning.jg4seasons.talk.realtime;

/**
 * 임시 키에 묶을 세션 설정. 지시문은 서버가 조립한 것만(브라우저는 주제·음성 code만 보낸다). toString에 지시문을 넣지 않는다(대화 설정이 로그에 남지 않게)
 *
 * @param voice 실제 음성 이름(marin 등)
 * @param keyTtlSeconds 임시 키 수명(초)
 */
public record RealtimeSessionRequest(
        String model, String instructions, String voice, String transcriptionModel, int keyTtlSeconds) {

    @Override
    public String toString() {
        return "RealtimeSessionRequest[model=" + model + ", voice=" + voice + ", transcriptionModel="
                + transcriptionModel + ", keyTtlSeconds=" + keyTtlSeconds + "]";
    }
}
