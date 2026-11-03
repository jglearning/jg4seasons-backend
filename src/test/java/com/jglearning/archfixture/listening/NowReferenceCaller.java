package com.jglearning.archfixture.listening;

import java.time.Instant;
import java.util.function.Supplier;

/** 위반: 메서드 참조로 now를 쓴다. */
public class NowReferenceCaller {
    public Supplier<Instant> supplier() {
        return Instant::now;
    }
}
