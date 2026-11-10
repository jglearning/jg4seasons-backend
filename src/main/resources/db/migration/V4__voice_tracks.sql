-- V4: 지문 트랙을 속도 대신 목소리로 구분한다(ADR-27).
-- 실제 음원은 1.0 속도 하나이고 같은 지문을 영어 목소리 4개가 각각 읽었다. 속도는 플레이어 재생 속도로 조절한다(0008 폐기).
-- 목소리 키: us_w(미국 여) · us_m(미국 남) · uk_w(영국 여) · uk_m(영국 남). 지문 트랙 variant는 p{지문 no}:{목소리}.
-- 트랙 행은 건드리지 않는다: 운영 DB는 콘텐츠 import 전에 다시 만들고, 로컬의 개발 시드 트랙은 import가 덮어쓴다.

-- 1) 레벨별 지문 목소리(개발 시드가 읽는다)
ALTER TABLE level RENAME COLUMN passage_speeds TO passage_voices;
UPDATE level SET passage_voices = JSON_ARRAY('us_w', 'us_m', 'uk_w', 'uk_m');

-- 2) 완료 규칙: 지문마다 아무 목소리 하나를 기준 이상 들으면 그 지문 완료
UPDATE completion_rule
SET required_tracks = JSON_ARRAY(JSON_OBJECT(
        'kind', 'passage',
        'eachPassage', TRUE,
        'anyOf', JSON_ARRAY('us_w', 'us_m', 'uk_w', 'uk_m')));

-- 3) 주석만
ALTER TABLE track MODIFY variant VARCHAR(12) NOT NULL COMMENT 'p1:us_w · w05:us_w';
ALTER TABLE sentence_timing
    MODIFY approximate BOOLEAN NOT NULL DEFAULT FALSE COMMENT '항상 FALSE(ADR-27: 목소리마다 문장 파일로 정확히 계산)';
ALTER TABLE sentence_timing
    MODIFY track_id BIGINT NOT NULL COMMENT '지문 트랙(kind=passage, 목소리별)';
