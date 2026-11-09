package com.jglearning.jg4seasons.auth.api;

import org.jspecify.annotations.Nullable;

/** 등록할 학생. 개인정보는 이름·학교·학년만. 정리(앞뒤 공백 제거, 빈 학교·학년 = null)는 StudentAccounts가 한다. */
public record NewStudent(
        String name, @Nullable String school, @Nullable String grade) {}
