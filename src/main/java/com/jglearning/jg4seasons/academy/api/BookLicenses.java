package com.jglearning.jg4seasons.academy.api;

import java.time.LocalDate;
import java.util.List;
import java.util.OptionalInt;

/** 다른 모듈이 쓰는 학원·권 사용권(ADR 0031). 판정은 여기 하나 — 차단은 호출하는 쪽(반 모듈)이 건다. */
public interface BookLicenses {

    /** today = 학원 타임존 날짜. 사용권을 적용하지 않는 학원 → ACTIVE, 적용 학원에 그 권 사용권이 없음 → EXPIRED */
    LicenseStatus statusOf(long academyId, long bookId, LocalDate today);

    /** 학생 수 한도. 사용권을 적용하지 않는 학원 → empty(무제한), 적용 학원에 사용권이 없음 → 0 */
    OptionalInt seatLimit(long academyId, long bookId);

    /** 학원의 사용권 줄, 권 id 순(기준 데이터 순서 = 레벨·권). 상태·남은 일수는 statusOf와 같은 판정. today = 학원 날짜 */
    List<LicenseRow> licensesOf(long academyId, LocalDate today);
}
