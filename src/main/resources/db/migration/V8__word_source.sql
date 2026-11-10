-- V8: 단어 출처를 저장한다(ADR-29).
-- book = 어휘책 그 강의 단어·숙어(녹음 자료에도 있는 단어 포함), extra = 어휘책에 없는 회차 어휘.
-- 기존 행은 book으로 두고 import 다시 실행이 extra를 채운다. 기본값은 바로 지워 넣는 쪽이 항상 값을 주게 한다.
ALTER TABLE word ADD COLUMN source VARCHAR(8) NOT NULL DEFAULT 'book'
    COMMENT 'book 어휘책 단어·숙어 / extra 어휘책에 없는 회차 어휘(ADR-29)' AFTER sort_order;
ALTER TABLE word ALTER COLUMN source DROP DEFAULT;
