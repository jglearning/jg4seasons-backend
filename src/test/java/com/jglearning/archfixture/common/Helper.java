package com.jglearning.archfixture.common;

import com.jglearning.archfixture.listening.session.SessionStore;

/** 위반: 모듈 목록에 없는 패키지에서 다른 모듈 내부를 부른다. */
public class Helper {
    public int count() {
        return new SessionStore().countSessions(1L, 1L);
    }
}
