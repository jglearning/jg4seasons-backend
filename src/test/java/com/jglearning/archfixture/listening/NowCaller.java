package com.jglearning.archfixture.listening;

import java.time.Instant;

/** 위반: 인자 없는 now()를 부른다. */
public class NowCaller {
    public Instant now() {
        return Instant.now();
    }
}
