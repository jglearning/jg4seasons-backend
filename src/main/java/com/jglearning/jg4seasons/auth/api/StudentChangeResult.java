package com.jglearning.jg4seasons.auth.api;

/** 변경 뒤 계정. deactivated = 이번 변경으로 active → inactive(퇴원)가 됐다 — 호출자가 소속 끝내기·감사 로그를 이것으로 정한다. */
public record StudentChangeResult(StudentAccount account, boolean deactivated) {}
