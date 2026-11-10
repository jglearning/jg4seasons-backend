-- V9: 연계 학원 교재 사용권(ADR-31).
-- 학원마다 사용권 적용 여부. 기존 학원(직영)은 적용 안 함 = 모든 권 무제한.
ALTER TABLE academy ADD COLUMN license_enforced BOOLEAN NOT NULL DEFAULT FALSE
    COMMENT '사용권 적용 여부(ADR-31). FALSE = 모든 권 무제한' AFTER settings;

-- 학원·권마다 한 줄. 재구매는 기간·학생 수를 늘린다. 날짜는 학원 타임존 날짜, 시작일·종료일 둘 다 포함.
CREATE TABLE book_license (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    academy_id      BIGINT       NOT NULL,
    book_id         BIGINT       NOT NULL,
    start_date      DATE         NOT NULL,
    end_date        DATE         NOT NULL,
    seats           INT          NOT NULL COMMENT '학생 수 한도(산 교재 부수)',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_book_license_academy_book (academy_id, book_id),
    CONSTRAINT ck_book_license_period CHECK (start_date <= end_date),
    CONSTRAINT ck_book_license_seats CHECK (seats >= 0),
    CONSTRAINT fk_book_license_academy FOREIGN KEY (academy_id) REFERENCES academy (id),
    CONSTRAINT fk_book_license_book FOREIGN KEY (book_id) REFERENCES book (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
