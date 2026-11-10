package com.jglearning.jg4seasons.clazz;

import com.jglearning.jg4seasons.infra.web.error.ErrorCode;
import org.springframework.http.HttpStatus;

/** 반 모듈의 오류 code. API 명세 §0.4 표(이슈 #29에서 추가). */
public enum ClassErrorCode implements ErrorCode {
    /** 학생 등록·이동 대상 반이 진행 중(active)이 아니다(결정 I) */
    CLASS_NOT_ACTIVE(HttpStatus.CONFLICT, "진행 중인 반이 아니에요"),
    /** 이동: 학생이 이미 그 반에만 소속이다(결정 I) */
    ALREADY_IN_CLASS(HttpStatus.CONFLICT, "이미 그 반 학생이에요"),
    /** 미래 회차(진도표상 수업일 전, 또는 휴강만 있음). 2.3·2.4·2.5·3.1 공통(StudentLessonGate) — 이슈 #37에서 듣기 모듈에서 옮김, code 그대로 */
    LESSON_LOCKED(HttpStatus.LOCKED, "아직 열리지 않은 회차예요"),
    /** 진도표: 오늘(학원 날짜) 이전 행의 날짜 변경·휴강(ADR 0030). 메모는 바꿀 수 있다 */
    SESSION_PAST(HttpStatus.CONFLICT, "지난 수업은 바꿀 수 없어요");

    private final HttpStatus status;
    private final String title;

    ClassErrorCode(HttpStatus status, String title) {
        this.status = status;
        this.title = title;
    }

    @Override
    public HttpStatus status() {
        return status;
    }

    @Override
    public String title() {
        return title;
    }
}
