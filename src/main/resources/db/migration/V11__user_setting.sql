-- V11: 학생이 고른 설정을 서버에 둔다(ADR-35). 지금은 지문 목소리 하나.
-- 학생 1명당 1행. 처음 저장할 때 만든다(조회만으로는 만들지 않는다).
-- 설정마다 타입 있는 칸을 두고 값은 DB가 제약한다. 설정이 늘면 칸을 더한다.
-- voice 칸은 글자 그대로 비교한다(utf8mb4_0900_bin). 표 기본 정렬(ai_ci)이면 CHECK가 'US_W'도 통과시킨다.
-- 학원 칸은 없다 — 읽는 쪽이 user를 조인해 학원 조건을 넣는다.

CREATE TABLE user_setting (
    user_id     BIGINT      NOT NULL,
    voice       VARCHAR(8)  CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NULL COMMENT '고른 지문 목소리. NULL = 고른 적 없음(기본값은 화면이 정한다)',
    created_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id),
    CONSTRAINT fk_user_setting_user FOREIGN KEY (user_id) REFERENCES `user` (id),
    CONSTRAINT ck_user_setting_voice CHECK (voice IS NULL OR voice IN ('us_w', 'us_m', 'uk_w', 'uk_m'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
