package com.jglearning.archfixture.listening;

import java.time.LocalDate;
import java.time.ZoneId;

/** 위반: 타임존만 주는 now는 시스템 시계를 읽는다. */
public class ZoneNowCaller {
    public LocalDate today() {
        return LocalDate.now(ZoneId.of("Asia/Seoul"));
    }
}
