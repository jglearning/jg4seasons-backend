-- V15: 캐릭터 프리토킹(1단계). 학생이 레벨 캐릭터와 실시간 음성으로 대화한다. 음성은 브라우저와 모델이 직접 주고받고 서버는 세션 기록·지시문·임시 키만 다룬다.
-- 2단계 이후 칸(사용량·요약·시작/끝 시각)도 지금 만들고 비워 둔다.
-- 값 칸은 ENUM 대신 VARCHAR + CHECK, 정렬은 utf8mb4_0900_bin(V12와 같음 — ai_ci면 CHECK가 대소문자를 가리지 못한다).
-- 학원 칸은 없다(word_test_submission과 같음 — 본인 확인은 JWT 학생, 교사 조회는 학원 학생 묶음으로).

-- 1) 대화 세션. created_at이 시작 요청 시각(하루 한도는 이것으로 센다)
CREATE TABLE talk_session (
    id                 BIGINT       NOT NULL AUTO_INCREMENT,
    client_session_id  CHAR(36)     CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '클라이언트 UUID — 같은 값이면 기존 세션',
    student_id         BIGINT       NOT NULL,
    lesson_id          BIGINT       NULL COMMENT '지시문에 넣은 회차. 오늘 회차가 없으면(개강 전) NULL',
    level_code         VARCHAR(10)  NOT NULL COMMENT '시작 때 반의 레벨 코드(L1~L4, K)',
    topic              VARCHAR(30)  CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NOT NULL,
    voice              VARCHAR(10)  CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NOT NULL,
    status             VARCHAR(10)  CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NOT NULL,
    max_seconds        SMALLINT     NOT NULL COMMENT '이 세션의 시간 제한(초)',
    duration_sec       SMALLINT     NULL COMMENT '실제 대화 시간(초, 브라우저가 알려준 값을 max_seconds로 자름)',
    student_turn_count SMALLINT     NULL,
    model              VARCHAR(40)  NOT NULL COMMENT '임시 키를 받은 모델(재현·전후 비교)',
    prompt_version     VARCHAR(40)  NOT NULL COMMENT '지시문 템플릿 버전(talk/v1)',
    usage_json         JSON         NULL COMMENT '모델 이벤트의 사용량 합(1단계는 브라우저가 전달 — 신뢰 불가)',
    summary_json       JSON         NULL COMMENT '끝난 뒤 요약. 만들지 못하면 NULL',
    started_at         DATETIME     NULL,
    ended_at           DATETIME     NULL,
    created_at         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_talk_session_client (client_session_id),
    KEY ix_talk_session_student_created (student_id, created_at),
    KEY ix_talk_session_lesson (lesson_id),
    CONSTRAINT fk_talk_session_student FOREIGN KEY (student_id) REFERENCES `user` (id),
    CONSTRAINT fk_talk_session_lesson FOREIGN KEY (lesson_id) REFERENCES lesson (id),
    CONSTRAINT ck_talk_session_topic CHECK (topic IN ('lesson', 'day', 'likes')),
    CONSTRAINT ck_talk_session_voice CHECK (voice IN ('female', 'male')),
    CONSTRAINT ck_talk_session_status CHECK (status IN ('created', 'ended', 'expired', 'failed')),
    CONSTRAINT ck_talk_session_max_seconds CHECK (max_seconds > 0),
    CONSTRAINT ck_talk_session_duration CHECK (duration_sec IS NULL OR duration_sec >= 0),
    CONSTRAINT ck_talk_session_turns CHECK (student_turn_count IS NULL OR student_turn_count >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- 2) 대화 글자 기록(음성은 저장하지 않는다). seq는 1부터 대화 순서
CREATE TABLE talk_turn (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    session_id  BIGINT       NOT NULL,
    seq         SMALLINT     NOT NULL,
    role        VARCHAR(10)  CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NOT NULL,
    text        VARCHAR(500) NOT NULL COMMENT '한 줄 500자까지(넘으면 받을 때 자름)',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_talk_turn_session_seq (session_id, seq),
    CONSTRAINT fk_talk_turn_session FOREIGN KEY (session_id) REFERENCES talk_session (id),
    CONSTRAINT ck_talk_turn_role CHECK (role IN ('student', 'character')),
    CONSTRAINT ck_talk_turn_seq CHECK (seq >= 1)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
