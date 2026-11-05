-- =====================================================================
-- V3: 빈칸 없앰, 지문 음원 하나 + 문장 시각(ADR-26, ERD v0.3 §8)
-- V1·V2는 고치지 않는다. 이 시점에 학생 학습 기록이 없어 데이터 이관 없음
-- =====================================================================

DROP TABLE cloze_progress;
DROP TABLE cloze_attempt;

ALTER TABLE lesson_progress
    DROP COLUMN cloze_total,
    DROP COLUMN cloze_correct;

ALTER TABLE completion_rule
    DROP COLUMN cloze_required_ratio,
    DROP COLUMN cloze_hint;

-- sentence: 빈칸 칸(앞·정답·뒤)과 문장 트랙 → 문장 원문 text 하나
ALTER TABLE sentence ADD COLUMN text VARCHAR(800) NULL COMMENT '문장 원문(화면 표시)' AFTER no;
UPDATE sentence SET text = CONCAT(text_before, blank_answer, text_after);
ALTER TABLE sentence MODIFY text VARCHAR(800) NOT NULL COMMENT '문장 원문(화면 표시)';
ALTER TABLE sentence DROP FOREIGN KEY fk_sentence_track;
ALTER TABLE sentence
    DROP INDEX uk_sentence_track,
    DROP COLUMN track_id,
    DROP COLUMN text_before,
    DROP COLUMN blank_answer,
    DROP COLUMN text_after;

-- track: 문장 파일은 지문 음원을 만드는 재료일 뿐 서빙하지 않는다.
-- 문장 트랙을 가리키는 청취 기록이 있으면 FK로 실패한다(기록을 지우지 않고 멈춤)
DELETE FROM track WHERE kind = 'sentence';
ALTER TABLE track
    MODIFY kind ENUM ('passage', 'word', 'word_example') NOT NULL,
    MODIFY variant VARCHAR(12) NOT NULL COMMENT 'p1:1.0 · w05';

-- 문장 시각: 지문 트랙(kind=passage, 속도별) 안에서 문장의 시작·끝. 가져오기 스크립트가 기록
CREATE TABLE sentence_timing (
    sentence_id     BIGINT       NOT NULL,
    track_id        BIGINT       NOT NULL COMMENT '지문 트랙(kind=passage, 속도별)',
    start_ms        INT          NOT NULL COMMENT '지문 음원 안에서 이 문장 시작',
    end_ms          INT          NOT NULL,
    approximate     BOOLEAN      NOT NULL DEFAULT FALSE COMMENT 'TRUE = 1.0 시각을 길이 비율로 늘이거나 줄인 근사',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (sentence_id, track_id),
    KEY ix_sentence_timing_track (track_id),
    CONSTRAINT fk_sentence_timing_sentence FOREIGN KEY (sentence_id) REFERENCES sentence (id),
    CONSTRAINT fk_sentence_timing_track FOREIGN KEY (track_id) REFERENCES track (id),
    CONSTRAINT ck_sentence_timing_range CHECK (start_ms >= 0 AND end_ms > start_ms)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
