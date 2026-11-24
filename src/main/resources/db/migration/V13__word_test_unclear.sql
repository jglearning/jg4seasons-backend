-- 읽을 수 없는 문항을 wrong이 아닌 unclear로 저장(11/25 결정). score는 correct 수라 unclear는 점수 밖, total에는 포함.
-- 교사 정정(teacher_result)은 판정을 내리는 칸이라 unclear를 받지 않는다
ALTER TABLE word_test_answer DROP CHECK ck_word_test_answer_result;

UPDATE word_test_answer SET result = 'unclear' WHERE unsure_reason = 'unreadable' AND result = 'wrong';

ALTER TABLE word_test_answer
    ADD CONSTRAINT ck_word_test_answer_result CHECK (result IN ('correct', 'wrong', 'blank', 'unclear'));
