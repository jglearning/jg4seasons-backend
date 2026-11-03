package com.jglearning.archfixture.cloze;

import com.jglearning.archfixture.listening.session.SessionStore;

/** 위반: 다른 모듈의 내부 패키지를 부른다. */
public class ClozeGrader {
    private final SessionStore sessionStore = new SessionStore();

    public int playCount(long studentId, long trackId) {
        return sessionStore.countSessions(studentId, trackId);
    }
}
