package com.jglearning.jg4seasons.auth.api;

import java.util.Optional;

/** 교사 계정 변경(API 명세 5.3 PATCH). 비어 있는 칸은 바꾸지 않는다. password는 평문 — 해시만 저장된다. */
public record StaffChange(Optional<String> name, Optional<AccountStatus> status, Optional<String> password) {

    public StaffChange {
        name = name == null ? Optional.empty() : name;
        status = status == null ? Optional.empty() : status;
        password = password == null ? Optional.empty() : password;
    }

    public static StaffChange rename(String name) {
        return new StaffChange(Optional.of(name), Optional.empty(), Optional.empty());
    }

    public static StaffChange status(AccountStatus status) {
        return new StaffChange(Optional.empty(), Optional.of(status), Optional.empty());
    }

    public static StaffChange password(String password) {
        return new StaffChange(Optional.empty(), Optional.empty(), Optional.of(password));
    }

    @Override
    public String toString() {
        return "StaffChange[name=" + name + ", status=" + status + ", password=" + (password.isPresent() ? "***" : "-")
                + "]";
    }
}
