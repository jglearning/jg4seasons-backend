package com.jglearning.jg4seasons.clazz.api;

import com.jglearning.jg4seasons.academy.api.LicenseRow;

/**
 * 사용권 한 줄과 사용 학생 수(7.3·7.5). usedSeats = 그 권 active 반에 지금 소속된 서로 다른 학생 수 — 등록·이동 한도(409 LICENSE_SEATS_FULL)와 같은
 * 숫자. 한도를 낮춘 경우 seats보다 클 수 있다.
 */
public record LicenseUsage(LicenseRow row, int usedSeats) {}
