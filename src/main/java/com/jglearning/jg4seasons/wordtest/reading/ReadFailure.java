package com.jglearning.jg4seasons.wordtest.reading;

/**
 * 답안지를 읽지 못함. 메시지에는 종류와 짧은 설명만(사진·응답 원문·키 없음) — 작업의 last_error에 그대로 들어간다. 사진 문제는 다시 해도 같아 재시도하지 않는다
 */
public class ReadFailure extends RuntimeException {

    public enum Kind {
        /** 제한 시간(30초) 초과 */
        TIMEOUT(true),
        /** 모델이 응답을 거절 */
        REFUSED(true),
        /** 응답이 max_tokens에서 잘림 */
        TRUNCATED(true),
        /** 응답 형식이 스키마와 다름 */
        MALFORMED(true),
        /** 문항 수·번호가 시험지와 다름(다른 쪽을 찍었거나 번호가 밀림) */
        COUNT_MISMATCH(true),
        /** 그 밖의 API·네트워크 오류(한도 429·서버 5xx·연결) */
        API_ERROR(true),
        /** API가 요청을 거절(400·413·422 — 사진 형식·크기 등, 다시 해도 같음) */
        REJECTED(false),
        /** 설정 오류(401·403·404 — 키·권한·모델 이름, 다시 해도 같음) */
        CONFIG(false),
        /** 사진이 없음(올라가지 않음·지워짐·키 없음) */
        PHOTO_MISSING(false),
        /** 사진이 상한보다 큼(제출 뒤 덮어씀) */
        PHOTO_TOO_LARGE(false);

        private final boolean retryable;

        Kind(boolean retryable) {
            this.retryable = retryable;
        }

        public boolean retryable() {
            return retryable;
        }
    }

    private final Kind kind;

    public ReadFailure(Kind kind, String detail) {
        super(kind.name() + ": " + detail);
        this.kind = kind;
    }

    public ReadFailure(Kind kind, String detail, Throwable cause) {
        super(kind.name() + ": " + detail, cause);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }
}
