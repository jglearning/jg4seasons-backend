package com.jglearning.jg4seasons.auth;

import com.jglearning.jg4seasons.infra.audio.AudioScope;
import java.util.List;

/**
 * 누가 어느 음원 범위를 받나(ADR 0031 결정 6). 쿠키 정책의 범위는 하나뿐이라 하나로 좁힐 수 없으면 전체를 준다.
 *
 * <ul>
 *   <li>운영자: 없음(음원 화면이 없다)
 *   <li>교사·관리자: 전체(여러 권 반을 다룬다)
 *   <li>학생 — 직영 학원: 전체 / 연계 학원: 쓸 수 있는 권이 0개면 없음, 1개면 그 권, 2개 이상이면 전체
 * </ul>
 */
final class AudioScopeRule {

    private AudioScopeRule() {}

    /** activeBookCodes = 현재 반의 권 중 학습할 수 있는(기간 안) 권 코드. 같은 권이 여러 번 와도 된다 */
    static AudioScope of(UserRole role, boolean licenseEnforced, List<String> activeBookCodes) {
        return switch (role) {
            case SUPERADMIN -> AudioScope.none();
            case TEACHER, ADMIN -> AudioScope.all();
            case STUDENT -> student(licenseEnforced, activeBookCodes);
        };
    }

    private static AudioScope student(boolean licenseEnforced, List<String> activeBookCodes) {
        if (!licenseEnforced) {
            return AudioScope.all();
        }
        List<String> books = activeBookCodes.stream().distinct().toList();
        if (books.isEmpty()) {
            return AudioScope.none();
        }
        return books.size() == 1 ? AudioScope.book(books.getFirst()) : AudioScope.all();
    }
}
