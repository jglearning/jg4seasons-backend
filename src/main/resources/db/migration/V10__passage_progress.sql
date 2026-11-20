-- V10: 목소리를 바꿔 들어도 지문 청취를 합친다(ADR-34, 0027 결정 2 변경).
-- 같은 지문이라도 목소리마다 음원 길이가 달라 ms 구간을 그대로 합칠 수 없다. 목소리별 들은 구간(track_progress.merged_ranges)을
-- 지문의 기준 시간축(필수 목소리 중 첫 트랙)으로 옮겨 합친 결과를 학생 × 지문마다 둔다.
-- 원본(listening_segment)과 목소리별 집계(track_progress)는 그대로다. 기존 행은 옮기지 않는다 — 그 지문의 다음 flush가 다시 만든다.

-- 1) 지문 단위 집계. 학원 칸은 없다(track_progress·lesson_progress와 같음 — 읽는 쪽은 학원 학생 묶음만 받는다)
CREATE TABLE passage_progress (
    student_id      BIGINT       NOT NULL,
    passage_id      BIGINT       NOT NULL,
    ref_track_id    BIGINT       NOT NULL COMMENT '기준 시간축 트랙(merged_ranges의 단위)',
    ref_duration_ms INT          NOT NULL COMMENT '계산할 때의 기준 트랙 길이 — 지금 길이와 다르면 구간·청취율을 쓰지 않는다',
    merged_ranges   JSON         NOT NULL COMMENT '목소리별 들은 구간을 기준 시간축 ms로 옮겨 합친 것 [[0,8120],[8400,61000]]',
    covered_ms      INT          NOT NULL DEFAULT 0,
    coverage        DECIMAL(5,4) NOT NULL DEFAULT 0 COMMENT 'covered_ms / ref_duration_ms, 1.0 상한',
    completed_at    DATETIME     NULL COMMENT '한 번 되면 유지',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (student_id, passage_id),
    KEY ix_passage_progress_passage (passage_id),
    CONSTRAINT fk_passage_progress_student FOREIGN KEY (student_id) REFERENCES `user` (id),
    CONSTRAINT fk_passage_progress_passage FOREIGN KEY (passage_id) REFERENCES passage (id),
    CONSTRAINT fk_passage_progress_ref_track FOREIGN KEY (ref_track_id) REFERENCES track (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- 2) 완료 규칙: 지문마다 목소리 하나를 기준 이상 들었거나(그대로), 여러 목소리로 들은 구간을 합친 청취율이 기준 이상이면 그 지문 완료.
--    합산 여부는 규칙 데이터에 둔다(ADR-07). combine이 없거나 false면 V4의 판정 그대로.
--    combine 키만 더한다 — 필수 목소리(anyOf)는 건드리지 않는다(레벨마다 바꿔 둔 값이 있어도 남는다)
UPDATE completion_rule
SET required_tracks = JSON_SET(required_tracks, '$[0].combine', TRUE);
