-- V12: 단어시험 사진 채점(1단계). 학생이 어휘책 "스스로 점검" 한 영역(한 쪽)을 찍어 올리면, 문항별로 손으로 쓴 글자를 읽고 코드가 정답과 비교한다.
-- 2단계(두 번 읽기·학생 확인·이의 제기·교사 정정) 칸과 상태도 지금 만들고 비워 둔다 — 흐름이 정해져 있어 2단계는 칸을 쓰기만 한다.
-- 값 칸은 ENUM 대신 VARCHAR + CHECK, 정렬은 utf8mb4_0900_bin(V11과 같음 — ai_ci면 CHECK가 대소문자를 가리지 못한다).
-- 제출·답안·작업 표에 학원 칸은 없다(track_progress·word_study와 같음 — 읽는 쪽은 학원 학생 묶음만 받는다).

-- 1) 시험지 문항 정의(공용 콘텐츠). 문항 순서가 단어 목록과 다르고 C·D 정답은 word에 없어서 word와 따로 둔다. 채점은 answer로 한다
CREATE TABLE word_test_item (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    lesson_id   BIGINT       NOT NULL,
    section     CHAR(1)      CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NOT NULL COMMENT 'A 뜻 쓰기 / B 철자 쓰기 / C 짧은 글 빈칸 / D 파생어 쓰기',
    no          SMALLINT     NOT NULL COMMENT '책의 문항 번호(영역마다 1부터)',
    word_id     BIGINT       NULL COMMENT '틀린 단어 복습 연결. 같은 철자 단어가 회차에 하나일 때만, 아니면 NULL',
    prompt      VARCHAR(200) NOT NULL COMMENT '문제로 준 쪽(B는 우리말 뜻)',
    answer      VARCHAR(100) NOT NULL COMMENT '정답 — 채점은 이 값으로',
    hint        VARCHAR(50)  NULL COMMENT '인쇄된 첫 글자(a, p... t...)',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_word_test_item_lesson_section_no (lesson_id, section, no),
    KEY ix_word_test_item_word (word_id),
    CONSTRAINT fk_word_test_item_lesson FOREIGN KEY (lesson_id) REFERENCES lesson (id),
    CONSTRAINT fk_word_test_item_word FOREIGN KEY (word_id) REFERENCES word (id),
    CONSTRAINT ck_word_test_item_section CHECK (section IN ('A', 'B', 'C', 'D')),
    CONSTRAINT ck_word_test_item_no CHECK (no >= 1)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- 2) 허용 답(두 철자가 모두 표준인 말, D의 괄호 안 다른 꼴)
CREATE TABLE word_test_accepted_answer (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    item_id     BIGINT       NOT NULL,
    text        VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NOT NULL,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_word_test_accepted_answer_item_text (item_id, text),
    CONSTRAINT fk_word_test_accepted_answer_item FOREIGN KEY (item_id) REFERENCES word_test_item (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- 3) 제출(회차 + 영역 하나 = 사진 한 장). created_at이 제출 시각
CREATE TABLE word_test_submission (
    id                   BIGINT       NOT NULL AUTO_INCREMENT,
    client_submission_id CHAR(36)     CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '클라이언트 UUID — 같은 값이면 기존 제출',
    student_id           BIGINT       NOT NULL,
    lesson_id            BIGINT       NOT NULL,
    section              CHAR(1)      CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NOT NULL,
    attempt_no           TINYINT      NOT NULL COMMENT '1 = 첫 제출',
    image_key            VARCHAR(200) NULL COMMENT 'homework/{academyId}/{studentId}/{uuid}.{ext}, 사진을 지우면 NULL',
    status               VARCHAR(12)  CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NOT NULL,
    score                SMALLINT     NULL COMMENT '맞은 문항 수, graded 전 NULL',
    total                SMALLINT     NOT NULL COMMENT '그 회차·영역의 문항 수(제출 때)',
    student_edit_count   TINYINT      NOT NULL DEFAULT 0 COMMENT '학생이 읽은 글자를 고친 문항 수(2단계)',
    disputed             BOOLEAN      NOT NULL DEFAULT FALSE COMMENT '학생 이의 제기(2단계)',
    model                VARCHAR(40)  NULL COMMENT '읽은 모델(재현·전후 비교)',
    prompt_version       VARCHAR(40)  NULL,
    graded_at            DATETIME     NULL,
    created_at           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_word_test_submission_client (client_submission_id),
    UNIQUE KEY uk_word_test_submission_attempt (student_id, lesson_id, section, attempt_no),
    KEY ix_word_test_submission_recent (student_id, lesson_id, section, created_at),
    KEY ix_word_test_submission_lesson (lesson_id),
    CONSTRAINT fk_word_test_submission_student FOREIGN KEY (student_id) REFERENCES `user` (id),
    CONSTRAINT fk_word_test_submission_lesson FOREIGN KEY (lesson_id) REFERENCES lesson (id),
    CONSTRAINT ck_word_test_submission_section CHECK (section IN ('A', 'B', 'C', 'D')),
    CONSTRAINT ck_word_test_submission_status
        CHECK (status IN ('pending', 'grading', 'confirming', 'graded', 'rejected', 'failed')),
    CONSTRAINT ck_word_test_submission_attempt CHECK (attempt_no >= 1)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- 4) 문항별 답안. 같은 제출을 두 번 처리해도 결과가 같게 (submission_id, item_id)로 upsert
CREATE TABLE word_test_answer (
    id                 BIGINT       NOT NULL AUTO_INCREMENT,
    submission_id      BIGINT       NOT NULL,
    item_id            BIGINT       NOT NULL,
    recognized_text    VARCHAR(100) NULL COMMENT '1차 읽기(손으로 쓴 글자 그대로), NULL = 빈칸',
    recognized_text_2  VARCHAR(100) NULL COMMENT '2차 읽기(2단계)',
    unsure_reason      VARCHAR(30)  CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NULL,
    student_text       VARCHAR(100) NULL COMMENT '학생이 확인·수정한 글자(2단계)',
    student_edited     BOOLEAN      NOT NULL DEFAULT FALSE,
    result             VARCHAR(8)   CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NOT NULL,
    teacher_result     VARCHAR(8)   CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NULL COMMENT '교사 정정(2단계)',
    teacher_id         BIGINT       NULL,
    teacher_updated_at DATETIME     NULL,
    created_at         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_word_test_answer_submission_item (submission_id, item_id),
    KEY ix_word_test_answer_item (item_id),
    CONSTRAINT fk_word_test_answer_submission FOREIGN KEY (submission_id) REFERENCES word_test_submission (id),
    CONSTRAINT fk_word_test_answer_item FOREIGN KEY (item_id) REFERENCES word_test_item (id),
    CONSTRAINT fk_word_test_answer_teacher FOREIGN KEY (teacher_id) REFERENCES `user` (id),
    CONSTRAINT ck_word_test_answer_result CHECK (result IN ('correct', 'wrong', 'blank')),
    CONSTRAINT ck_word_test_answer_teacher_result
        CHECK (teacher_result IS NULL OR teacher_result IN ('correct', 'wrong', 'blank')),
    CONSTRAINT ck_word_test_answer_unsure
        CHECK (unsure_reason IS NULL OR unsure_reason IN ('read_mismatch', 'near_miss', 'unreadable'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- 5) 채점 작업 큐. 제출과 같은 트랜잭션에서 만들고, 워커가 SELECT ... FOR UPDATE SKIP LOCKED로 잡는다
CREATE TABLE grading_job (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    submission_id BIGINT       NOT NULL,
    status        VARCHAR(8)   CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NOT NULL,
    run_after     DATETIME(3)  NOT NULL COMMENT '이 시각 이후에 잡는다(재시도 지연)',
    attempts      TINYINT      NOT NULL DEFAULT 0,
    last_error    VARCHAR(500) NULL COMMENT '실패 종류만 — 사진·응답 원문 없음',
    locked_at     DATETIME(3)  NULL COMMENT 'running으로 바꾼 시각(2단계 죽은 작업 회수 기준)',
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_grading_job_submission (submission_id),
    KEY ix_grading_job_ready (status, run_after),
    CONSTRAINT fk_grading_job_submission FOREIGN KEY (submission_id) REFERENCES word_test_submission (id),
    CONSTRAINT ck_grading_job_status CHECK (status IN ('queued', 'running', 'done', 'dead'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
