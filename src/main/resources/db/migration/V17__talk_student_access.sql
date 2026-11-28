-- V17: 프리토킹 학생 허용(ADR 0040). 운영자가 학생마다 켜고 끈다. 학원 스위치(settings features.talk)와 AND.
-- 행이 없으면 꺼짐(새 학생 기본 꺼짐, 기존 데이터 채우기 없음). 구독(talkEntitled, #80)과는 별도 — 꺼지면 숨김(404), 구독 없음은 잠금(403).
CREATE TABLE talk_student_access (
    id          BIGINT   NOT NULL AUTO_INCREMENT,
    academy_id  BIGINT   NOT NULL,
    student_id  BIGINT   NOT NULL,
    enabled     BOOLEAN  NOT NULL,
    changed_by  BIGINT   NOT NULL COMMENT '마지막으로 바꾼 운영자',
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_talk_student_access_student (student_id),
    KEY ix_talk_student_access_academy (academy_id, enabled),
    CONSTRAINT fk_talk_student_access_academy FOREIGN KEY (academy_id) REFERENCES academy (id),
    CONSTRAINT fk_talk_student_access_student FOREIGN KEY (student_id) REFERENCES `user` (id),
    CONSTRAINT fk_talk_student_access_changed_by FOREIGN KEY (changed_by) REFERENCES `user` (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
