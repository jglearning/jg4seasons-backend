-- 1차 스키마 (ERD v0.2). 머지된 뒤에는 고치지 않는다. 바꿀 것은 새 버전으로.
-- 시각은 UTC 저장. 회차 열림·진도표 날짜 판정은 학원 타임존(기본 Asia/Seoul) 날짜로 애플리케이션에서 한다.

-- =====================================================================
-- 1. 공용 콘텐츠 (academy_id 없음, 회차 코드가 자연키)
-- =====================================================================

CREATE TABLE level (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    code            VARCHAR(2)   NOT NULL,
    name_ko         VARCHAR(20)  NOT NULL,
    name_en         VARCHAR(20)  NOT NULL,
    color_hex       CHAR(7)      NOT NULL,
    sort_order      TINYINT      NOT NULL,
    passage_speeds  JSON         NOT NULL,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_level_code (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE book (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    level_id        BIGINT       NOT NULL,
    season          TINYINT      NOT NULL COMMENT '1 겨울 · 2 봄 · 3 여름 · 4 가을',
    code            VARCHAR(6)   NOT NULL,
    title           VARCHAR(50)  NOT NULL,
    lesson_count    TINYINT      NOT NULL DEFAULT 24,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_book_code (code),
    UNIQUE KEY uk_book_level_season (level_id, season),
    CONSTRAINT fk_book_level FOREIGN KEY (level_id) REFERENCES level (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE lesson (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    book_id         BIGINT       NOT NULL,
    code            VARCHAR(10)  NOT NULL COMMENT '회차 자연키 L2-B1-05',
    number          TINYINT      NOT NULL,
    module          TINYINT      NOT NULL,
    type            ENUM ('regular', 'module_review', 'integrated') NOT NULL,
    title           VARCHAR(100) NOT NULL,
    passage_count   TINYINT      NOT NULL DEFAULT 6,
    concept_a       JSON         NULL,
    concept_b       JSON         NULL,
    memo_check      TEXT         NULL,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_lesson_code (code),
    UNIQUE KEY uk_lesson_book_number (book_id, number),
    CONSTRAINT fk_lesson_book FOREIGN KEY (book_id) REFERENCES book (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE passage (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    lesson_id       BIGINT       NOT NULL,
    no              TINYINT      NOT NULL,
    title           VARCHAR(120) NOT NULL,
    word_count      SMALLINT     NOT NULL,
    body            TEXT         NULL,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_passage_lesson_no (lesson_id, no),
    CONSTRAINT fk_passage_lesson FOREIGN KEY (lesson_id) REFERENCES lesson (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE track (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    lesson_id       BIGINT       NOT NULL,
    passage_id      BIGINT       NULL,
    kind            ENUM ('passage', 'sentence', 'word', 'word_example') NOT NULL,
    variant         VARCHAR(12)  NOT NULL COMMENT 'p1:1.0 · p1-s03 · w05',
    file_key        VARCHAR(200) NOT NULL COMMENT 'S3 키',
    duration_ms     INT          NOT NULL COMMENT '청취율 분모',
    sort_order      SMALLINT     NOT NULL,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_track_lesson_kind_variant (lesson_id, kind, variant),
    UNIQUE KEY uk_track_file_key (file_key),
    CONSTRAINT fk_track_lesson FOREIGN KEY (lesson_id) REFERENCES lesson (id),
    CONSTRAINT fk_track_passage FOREIGN KEY (passage_id) REFERENCES passage (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE sentence (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    passage_id      BIGINT       NOT NULL,
    no              TINYINT      NOT NULL,
    text_before     VARCHAR(400) NOT NULL,
    blank_answer    VARCHAR(60)  NOT NULL COMMENT 'API로 내보내지 않음(ADR-23)',
    text_after      VARCHAR(400) NOT NULL,
    speaker         VARCHAR(20)  NULL,
    track_id        BIGINT       NULL COMMENT 'kind=sentence 트랙',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_sentence_passage_no (passage_id, no),
    UNIQUE KEY uk_sentence_track (track_id),
    CONSTRAINT fk_sentence_passage FOREIGN KEY (passage_id) REFERENCES passage (id),
    CONSTRAINT fk_sentence_track FOREIGN KEY (track_id) REFERENCES track (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE grammar_item (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    code            VARCHAR(6)   NOT NULL,
    part            CHAR(2)      NOT NULL,
    name            VARCHAR(100) NOT NULL,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_grammar_item_code (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE lesson_grammar_item (
    lesson_id       BIGINT       NOT NULL,
    grammar_item_id BIGINT       NOT NULL,
    stage           ENUM ('encounter', 'basic', 'advanced', 'complete') NULL,
    is_primary      BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (lesson_id, grammar_item_id),
    CONSTRAINT fk_lgi_lesson FOREIGN KEY (lesson_id) REFERENCES lesson (id),
    CONSTRAINT fk_lgi_grammar_item FOREIGN KEY (grammar_item_id) REFERENCES grammar_item (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE word (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    lesson_id        BIGINT       NOT NULL,
    passage_id       BIGINT       NOT NULL COMMENT '단어가 나온 지문',
    sort_order       TINYINT      NOT NULL,
    english          VARCHAR(50)  NOT NULL,
    korean           VARCHAR(100) NOT NULL,
    pos              VARCHAR(8)   NOT NULL COMMENT 'n v adj adv prep conj phr',
    example_en       VARCHAR(300) NOT NULL COMMENT '새 예문(지문 문장 아님, ADR-25)',
    example_ko       VARCHAR(300) NOT NULL,
    moe_level        TINYINT      NULL COMMENT '교육부 1·2·3단계, NULL = 목록 외',
    track_id         BIGINT       NULL,
    example_track_id BIGINT       NULL,
    created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_word_lesson_sort (lesson_id, sort_order),
    KEY ix_word_passage (passage_id),
    CONSTRAINT fk_word_lesson FOREIGN KEY (lesson_id) REFERENCES lesson (id),
    CONSTRAINT fk_word_passage FOREIGN KEY (passage_id) REFERENCES passage (id),
    CONSTRAINT fk_word_track FOREIGN KEY (track_id) REFERENCES track (id),
    CONSTRAINT fk_word_example_track FOREIGN KEY (example_track_id) REFERENCES track (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE completion_rule (
    id                   BIGINT       NOT NULL AUTO_INCREMENT,
    level_id             BIGINT       NOT NULL,
    coverage_threshold   DECIMAL(4,3) NOT NULL DEFAULT 0.900,
    required_tracks      JSON         NOT NULL,
    cloze_required_ratio DECIMAL(4,3) NULL COMMENT 'NULL = 완료 조건 아님(O-10)',
    cloze_hint           JSON         NOT NULL COMMENT '{"firstLetterAt":3,"answerAt":4}',
    created_at           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_completion_rule_level (level_id),
    CONSTRAINT fk_completion_rule_level FOREIGN KEY (level_id) REFERENCES level (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- =====================================================================
-- 2. 학원 (테넌트). 모든 조회에 academy_id 조건
-- =====================================================================

CREATE TABLE academy (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    name            VARCHAR(50)  NOT NULL,
    slug            VARCHAR(30)  NOT NULL,
    status          ENUM ('active', 'suspended') NOT NULL DEFAULT 'active',
    settings        JSON         NULL COMMENT '타임존(기본 Asia/Seoul) 등',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_academy_slug (slug)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- 학생 개인정보는 이름·학교·학년만(전화·생년월일·이메일 컬럼 금지)
CREATE TABLE `user` (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    academy_id      BIGINT       NULL COMMENT '시스템 관리자는 NULL',
    role            ENUM ('student', 'teacher', 'admin', 'superadmin') NOT NULL,
    name            VARCHAR(30)  NOT NULL,
    school          VARCHAR(50)  NULL,
    grade           VARCHAR(10)  NULL,
    status          ENUM ('active', 'inactive') NOT NULL DEFAULT 'active',
    last_login_at   DATETIME     NULL,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY ix_user_academy_role_status (academy_id, role, status),
    CONSTRAINT fk_user_academy FOREIGN KEY (academy_id) REFERENCES academy (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- personal_code의 identifier는 HMAC-SHA256(pepper, academyId:code) hex. 평문 없음(ADR-22)
CREATE TABLE auth_identity (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    user_id         BIGINT       NOT NULL,
    provider        ENUM ('personal_code', 'password', 'kakao') NOT NULL,
    identifier      VARCHAR(100) NULL,
    secret_hash     VARCHAR(100) NULL COMMENT 'password: bcrypt',
    failed_attempts TINYINT      NOT NULL DEFAULT 0,
    locked_until    DATETIME     NULL,
    issued_at       DATETIME     NULL,
    last_used_at    DATETIME     NULL,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_auth_identity_user_provider (user_id, provider),
    UNIQUE KEY uk_auth_identity_provider_identifier (provider, identifier),
    CONSTRAINT fk_auth_identity_user FOREIGN KEY (user_id) REFERENCES `user` (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE `class` (
    id                   BIGINT       NOT NULL AUTO_INCREMENT,
    academy_id           BIGINT       NOT NULL,
    book_id              BIGINT       NOT NULL,
    name                 VARCHAR(50)  NOT NULL,
    start_date           DATE         NOT NULL,
    weekdays             VARCHAR(7)   NOT NULL COMMENT '월~일 비트 문자열 0101000',
    access_code          CHAR(8)      NOT NULL COMMENT '가입 코드',
    code_issued_at       DATETIME     NOT NULL,
    allow_future_lessons BOOLEAN      NOT NULL DEFAULT FALSE,
    status               ENUM ('active', 'finished', 'archived') NOT NULL DEFAULT 'active',
    created_at           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_class_access_code (access_code),
    KEY ix_class_academy_status (academy_id, status),
    CONSTRAINT fk_class_academy FOREIGN KEY (academy_id) REFERENCES academy (id),
    CONSTRAINT fk_class_book FOREIGN KEY (book_id) REFERENCES book (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE class_teacher (
    class_id        BIGINT       NOT NULL,
    user_id         BIGINT       NOT NULL,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (class_id, user_id),
    CONSTRAINT fk_class_teacher_class FOREIGN KEY (class_id) REFERENCES `class` (id),
    CONSTRAINT fk_class_teacher_user FOREIGN KEY (user_id) REFERENCES `user` (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE enrollment (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    class_id        BIGINT       NOT NULL,
    student_id      BIGINT       NOT NULL,
    joined_at       DATE         NOT NULL,
    left_at         DATE         NULL COMMENT 'NULL = 현재 소속',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY ix_enrollment_class_left (class_id, left_at),
    KEY ix_enrollment_student_left (student_id, left_at),
    CONSTRAINT fk_enrollment_class FOREIGN KEY (class_id) REFERENCES `class` (id),
    CONSTRAINT fk_enrollment_student FOREIGN KEY (student_id) REFERENCES `user` (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE class_session (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    class_id        BIGINT       NOT NULL,
    seq             TINYINT      NOT NULL,
    lesson_id       BIGINT       NOT NULL,
    scheduled_date  DATE         NOT NULL,
    status          ENUM ('scheduled', 'done', 'cancelled') NOT NULL DEFAULT 'scheduled',
    note            VARCHAR(100) NULL,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_class_session_class_seq (class_id, seq),
    KEY ix_class_session_class_date (class_id, scheduled_date),
    CONSTRAINT fk_class_session_class FOREIGN KEY (class_id) REFERENCES `class` (id),
    CONSTRAINT fk_class_session_lesson FOREIGN KEY (lesson_id) REFERENCES lesson (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- =====================================================================
-- 3. 학습 기록. 원본(listening_segment, cloze_attempt)은 추가만, 화면은 집계만 읽는다(ADR-06)
-- =====================================================================

CREATE TABLE listening_session (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    client_session_id CHAR(36)     NOT NULL COMMENT '클라이언트 UUID, 재전송 멱등 키',
    student_id        BIGINT       NOT NULL,
    track_id          BIGINT       NOT NULL,
    started_at        DATETIME     NOT NULL,
    last_seen_at      DATETIME     NOT NULL,
    user_agent        VARCHAR(200) NULL,
    created_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_listening_session_client (client_session_id),
    KEY ix_listening_session_student_track (student_id, track_id),
    CONSTRAINT fk_listening_session_student FOREIGN KEY (student_id) REFERENCES `user` (id),
    CONSTRAINT fk_listening_session_track FOREIGN KEY (track_id) REFERENCES track (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE listening_segment (
    id                   BIGINT       NOT NULL AUTO_INCREMENT,
    listening_session_id BIGINT       NOT NULL,
    start_ms             INT          NOT NULL,
    end_ms               INT          NOT NULL,
    recorded_at          DATETIME(3)  NOT NULL COMMENT '클라이언트 시각',
    created_at           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY ix_listening_segment_session (listening_session_id),
    CONSTRAINT fk_listening_segment_session FOREIGN KEY (listening_session_id) REFERENCES listening_session (id),
    CONSTRAINT ck_listening_segment_range CHECK (start_ms >= 0 AND end_ms > start_ms)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE track_progress (
    student_id      BIGINT       NOT NULL,
    track_id        BIGINT       NOT NULL,
    merged_ranges   JSON         NOT NULL COMMENT '[[0,8120],[8400,61000]] ms',
    covered_ms      INT          NOT NULL DEFAULT 0,
    coverage        DECIMAL(5,4) NOT NULL DEFAULT 0 COMMENT 'covered_ms / duration_ms, 1.0 상한',
    completed_at    DATETIME     NULL,
    total_play_ms   INT          NOT NULL DEFAULT 0 COMMENT '반복 포함, 정직성 지표',
    session_count   SMALLINT     NOT NULL DEFAULT 0,
    last_played_at  DATETIME     NOT NULL,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (student_id, track_id),
    KEY ix_track_progress_track (track_id),
    CONSTRAINT fk_track_progress_student FOREIGN KEY (student_id) REFERENCES `user` (id),
    CONSTRAINT fk_track_progress_track FOREIGN KEY (track_id) REFERENCES track (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE lesson_progress (
    student_id      BIGINT       NOT NULL,
    lesson_id       BIGINT       NOT NULL,
    status          ENUM ('not_started', 'in_progress', 'completed') NOT NULL,
    required_total  TINYINT      NOT NULL DEFAULT 0,
    required_done   TINYINT      NOT NULL DEFAULT 0,
    passages_total  TINYINT      NOT NULL DEFAULT 0,
    passages_done   TINYINT      NOT NULL DEFAULT 0,
    cloze_total     SMALLINT     NOT NULL DEFAULT 0,
    cloze_correct   SMALLINT     NOT NULL DEFAULT 0,
    completed_at    DATETIME     NULL,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (student_id, lesson_id),
    KEY ix_lesson_progress_lesson_status (lesson_id, status),
    CONSTRAINT fk_lesson_progress_student FOREIGN KEY (student_id) REFERENCES `user` (id),
    CONSTRAINT fk_lesson_progress_lesson FOREIGN KEY (lesson_id) REFERENCES lesson (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE cloze_attempt (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    student_id      BIGINT       NOT NULL,
    sentence_id     BIGINT       NOT NULL,
    answer          VARCHAR(60)  NOT NULL COMMENT '정규화 전 원문',
    correct         BOOLEAN      NOT NULL,
    play_count      SMALLINT     NOT NULL COMMENT '채점 시점의 문장 재생 횟수',
    hint_shown      ENUM ('none', 'first_letter', 'answer') NOT NULL,
    created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY ix_cloze_attempt_student_sentence (student_id, sentence_id),
    CONSTRAINT fk_cloze_attempt_student FOREIGN KEY (student_id) REFERENCES `user` (id),
    CONSTRAINT fk_cloze_attempt_sentence FOREIGN KEY (sentence_id) REFERENCES sentence (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE cloze_progress (
    student_id      BIGINT       NOT NULL,
    sentence_id     BIGINT       NOT NULL,
    lesson_id       BIGINT       NOT NULL COMMENT '회차 집계용(비정규화)',
    attempts        SMALLINT     NOT NULL DEFAULT 0,
    correct_at      DATETIME     NULL,
    hint_at_correct ENUM ('none', 'first_letter', 'answer') NULL,
    last_answer     VARCHAR(60)  NOT NULL,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (student_id, sentence_id),
    KEY ix_cloze_progress_student_lesson (student_id, lesson_id),
    CONSTRAINT fk_cloze_progress_student FOREIGN KEY (student_id) REFERENCES `user` (id),
    CONSTRAINT fk_cloze_progress_sentence FOREIGN KEY (sentence_id) REFERENCES sentence (id),
    CONSTRAINT fk_cloze_progress_lesson FOREIGN KEY (lesson_id) REFERENCES lesson (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- 모드별 값은 단조 증가(작은 값은 무시). 단어별 기록은 1차 없음(ADR-25)
CREATE TABLE word_study (
    student_id      BIGINT       NOT NULL,
    lesson_id       BIGINT       NOT NULL,
    cards_viewed    TINYINT      NOT NULL DEFAULT 0,
    all_viewed_at   DATETIME     NULL,
    mem_known       TINYINT      NOT NULL DEFAULT 0,
    recall_best     TINYINT      NOT NULL DEFAULT 0,
    spell_best      TINYINT      NOT NULL DEFAULT 0,
    last_viewed_at  DATETIME     NOT NULL,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (student_id, lesson_id),
    CONSTRAINT fk_word_study_student FOREIGN KEY (student_id) REFERENCES `user` (id),
    CONSTRAINT fk_word_study_lesson FOREIGN KEY (lesson_id) REFERENCES lesson (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- =====================================================================
-- 4. 인증·운영
-- =====================================================================

CREATE TABLE refresh_token (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    user_id         BIGINT       NOT NULL,
    token_hash      CHAR(64)     NOT NULL COMMENT '쿠키 refresh 값의 SHA-256',
    expires_at      DATETIME     NOT NULL,
    revoked_at      DATETIME     NULL,
    user_agent      VARCHAR(200) NULL,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_refresh_token_hash (token_hash),
    KEY ix_refresh_token_user (user_id),
    CONSTRAINT fk_refresh_token_user FOREIGN KEY (user_id) REFERENCES `user` (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- 추가만, 조회는 관리자
CREATE TABLE audit_log (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    academy_id      BIGINT       NOT NULL,
    actor_id        BIGINT       NOT NULL,
    action          VARCHAR(40)  NOT NULL COMMENT 'PERSONAL_CODE_REGENERATE · CODE_REGENERATE · STUDENT_MOVE · STUDENT_DEACTIVATE',
    target_type     VARCHAR(20)  NOT NULL,
    target_id       BIGINT       NOT NULL,
    detail          JSON         NULL,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY ix_audit_log_academy_created (academy_id, created_at),
    CONSTRAINT fk_audit_log_academy FOREIGN KEY (academy_id) REFERENCES academy (id),
    CONSTRAINT fk_audit_log_actor FOREIGN KEY (actor_id) REFERENCES `user` (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- academy_id 0 = 전체 합계라 FK 없음
CREATE TABLE daily_metric (
    `date`            DATE         NOT NULL,
    academy_id        BIGINT       NOT NULL,
    active_students   INT          NOT NULL DEFAULT 0,
    segments          INT          NOT NULL DEFAULT 0,
    completed_lessons INT          NOT NULL DEFAULT 0,
    flush_failures    INT          NOT NULL DEFAULT 0 COMMENT '클라이언트 보고(2차)',
    created_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`date`, academy_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
