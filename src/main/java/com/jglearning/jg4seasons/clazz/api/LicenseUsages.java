package com.jglearning.jg4seasons.clazz.api;

import java.time.LocalDate;
import java.util.List;

/** 학원의 권별 사용권 현황(운영자 7.3, 학원관리자 7.5). 다른 모듈(system)이 쓴다. 학원 범위 확인은 호출자가 먼저 한다. */
public interface LicenseUsages {

    /** 사용권 줄이 있는 권만, 권 id 순. today = 학원 타임존 날짜 */
    List<LicenseUsage> of(long academyId, LocalDate today);
}
