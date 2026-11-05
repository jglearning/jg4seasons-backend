package com.jglearning.archfixture.words;

import com.jglearning.archfixture.listening.session.SessionStore;

/** 위반: 다른 모듈의 내부 패키지를 부른다(규칙 확인용 가상의 의존 — 실제 words 설계와 무관). */
public class WordStudyRecorder {
    private final SessionStore sessionStore = new SessionStore();

    public int playCount(long studentId, long trackId) {
        return sessionStore.countSessions(studentId, trackId);
    }
}
