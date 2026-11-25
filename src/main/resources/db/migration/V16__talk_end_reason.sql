-- V16: 프리토킹 종료 이유(#77). 종료 요청의 endReason(user · timeout · left · disconnected)을 남겨 시간 종료·이탈 비율을 셀 수 있게 한다.
-- 종료 전 세션과 V15 행은 NULL. 값 칸 정렬은 utf8mb4_0900_bin(V12·V15와 같음 — CHECK가 대소문자를 가린다)
ALTER TABLE talk_session
    ADD COLUMN end_reason VARCHAR(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NULL
        COMMENT '종료 요청의 이유. 종료 전이면 NULL' AFTER ended_at,
    ADD CONSTRAINT ck_talk_session_end_reason
        CHECK (end_reason IS NULL OR end_reason IN ('user', 'timeout', 'left', 'disconnected'));
