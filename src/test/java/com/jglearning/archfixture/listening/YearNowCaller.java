package com.jglearning.archfixture.listening;

import java.time.Year;

/** 위반: Year.now()도 시스템 시계를 읽는다. */
public class YearNowCaller {
    public Year year() {
        return Year.now();
    }
}
