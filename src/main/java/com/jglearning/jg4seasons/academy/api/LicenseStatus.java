package com.jglearning.jg4seasons.academy.api;

/**
 * 학원·권의 사용 상태(ADR 0031 결정 4). value = API 값. 사용권을 적용하지 않는 학원(직영)은 늘 ACTIVE, 적용 학원에 사용권이 없는 권은 EXPIRED.
 * 만료 뒤 동작을 바꿀 때는 판정(academy.LicenseRule)과 이 두 허용 메서드만 고친다.
 */
public enum LicenseStatus {
    /** 오늘(학원 날짜) < 시작일 */
    BEFORE_START("before_start"),
    /** 시작일 ≤ 오늘 ≤ 종료일, 또는 사용권을 적용하지 않는 학원 */
    ACTIVE("active"),
    /** 종료일 < 오늘, 또는 사용권 없음 */
    EXPIRED("expired");

    private final String value;

    LicenseStatus(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    /** 학생 학습(회차 상세·단어·학습 결과·청취 기록): ACTIVE만 */
    public boolean allowsLearning() {
        // 상태를 더하면(유예 기간 등) 컴파일러가 이 결정을 요구하도록 모두 나열한다
        return switch (this) {
            case ACTIVE -> true;
            case BEFORE_START, EXPIRED -> false;
        };
    }

    /** 개강 준비(새 반·진도표 편집·그 권 반으로 등록·이동): BEFORE_START·ACTIVE */
    public boolean allowsPreparation() {
        return switch (this) {
            case BEFORE_START, ACTIVE -> true;
            case EXPIRED -> false;
        };
    }
}
