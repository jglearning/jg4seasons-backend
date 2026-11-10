-- V7: 어휘책 단어를 함께 넣는다(ADR-27 결정 6, 2026-11-10 수정).
-- 어휘책에만 있는 단어는 예문 해석과 음원이 없다(track_id·example_track_id는 V1부터 NULL 허용). 품사에 대명사·감탄사가 있다.
ALTER TABLE word MODIFY example_ko VARCHAR(300) NULL COMMENT '예문 해석, NULL = 없음(어휘책에만 있는 단어)';
ALTER TABLE word MODIFY pos VARCHAR(8) NOT NULL COMMENT 'n v adj adv prep conj pron interj phr';
