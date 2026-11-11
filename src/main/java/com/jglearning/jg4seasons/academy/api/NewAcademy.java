package com.jglearning.jg4seasons.academy.api;

import java.time.ZoneId;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 새 학원. name은 정리(strip)해 1~50자, slug는 AcademyRegistry.SLUG_REGEXP, timezone은 진도표·회차 열림의 "오늘" 기준. 규칙을 어기면
 * IllegalArgumentException — 사용자 문구(400)는 요청 DTO가 먼저 맡는다.
 */
public record NewAcademy(String name, String slug, ZoneId timezone, boolean licenseEnforced) {

    /** academy.name VARCHAR(50) */
    public static final int MAX_NAME = 50;

    private static final Pattern SLUG = Pattern.compile(AcademyRegistry.SLUG_REGEXP);

    public NewAcademy {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(slug, "slug");
        Objects.requireNonNull(timezone, "timezone");
        name = name.strip();
        if (name.isEmpty() || name.length() > MAX_NAME) {
            throw new IllegalArgumentException("학원 이름은 정리 뒤 1~" + MAX_NAME + "자여야 한다");
        }
        if (!SLUG.matcher(slug).matches()) {
            throw new IllegalArgumentException("학원 주소 규칙(" + AcademyRegistry.SLUG_REGEXP + ")에 맞지 않는다");
        }
    }
}
