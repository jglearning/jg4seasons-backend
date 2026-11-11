package com.jglearning.jg4seasons.academy.api;

import java.time.ZoneId;
import java.util.Objects;

/** 새 학원. name은 정리(strip)된 1~50자, slug는 AcademyRegistry.SLUG_REGEXP, timezone은 진도표·회차 열림의 "오늘" 기준. */
public record NewAcademy(String name, String slug, ZoneId timezone, boolean licenseEnforced) {

    public NewAcademy {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(slug, "slug");
        Objects.requireNonNull(timezone, "timezone");
    }
}
