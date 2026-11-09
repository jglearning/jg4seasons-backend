package com.jglearning.jg4seasons.auth.api;

import java.util.Optional;

/** 반 전체 로그인 실패 경고(AUTH-07). 교사 반 목록(API 명세 4.1 loginAlert)이 쓴다. */
public interface LoginAlerts {

    /** 최근 1시간에 그 반의 개인 코드 실패가 20회 이상이면 경고, 아니면 empty */
    Optional<LoginAlert> alertOf(long classId);
}
