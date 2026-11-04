package com.jglearning.jg4seasons.auth.api;

/** 새 교사·관리자. loginId는 요청 그대로(서비스가 정리·검사), password는 평문 — 해시만 저장된다. */
public record NewStaff(String name, String loginId, String password, StaffRole role) {

    @Override
    public String toString() {
        return "NewStaff[name=" + name + ", loginId=" + loginId + ", role=" + role + "]";
    }
}
