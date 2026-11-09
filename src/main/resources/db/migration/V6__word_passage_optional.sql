-- V6: 단어의 지문 연결을 선택으로 한다(ADR-27).
-- 녹음 자료의 단어는 회차 어휘라 지문 하나를 가리키지 않는다. import는 passage_id를 NULL로 넣는다.
ALTER TABLE word MODIFY passage_id BIGINT NULL COMMENT '단어가 나온 지문, NULL = 회차 어휘(ADR-27)';
