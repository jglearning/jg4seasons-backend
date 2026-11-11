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

    /**
     * 학원·권 사용권을 지정한다(7.4): 줄이 없으면 만들고 있으면 고친다(줄이기 허용). 종료일 < 시작일이면 400 VALIDATION(endDate), 학생 수 0~10000 밖이면
     * 400(seats), 학원이 없으면 404. 같은 학원·권의 동시 지정은 스스로 줄 세운다(학원 행 잠금 + 사용권 줄 잠금 읽기 — 호출자가 앞서 무엇을 읽었든). 호출자
     * 트랜잭션 안에서만(MANDATORY — 감사 기록과 함께 롤백). today = 학원 날짜(돌려주는 줄의 상태 계산용)
     */
    LicenseWrite setLicense(
            long academyId, long bookId, LocalDate startDate, LocalDate endDate, int seats, LocalDate today);
}
