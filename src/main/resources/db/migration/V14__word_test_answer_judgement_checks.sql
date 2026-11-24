-- 답안 판정 규칙을 DB에서도(코드의 Judgement·GradedAnswer와 같음, #72 리뷰).
-- unclear는 이유가 있어야 하고, 읽을 수 없음(unreadable)은 unclear와만 짝짓는다. 2단계 사유(read_mismatch·near_miss)의 짝은 열어 둔다
ALTER TABLE word_test_answer
    ADD CONSTRAINT ck_word_test_answer_unclear_reason CHECK (result <> 'unclear' OR unsure_reason IS NOT NULL),
    ADD CONSTRAINT ck_word_test_answer_unreadable
        CHECK (unsure_reason IS NULL OR unsure_reason <> 'unreadable' OR result = 'unclear');
