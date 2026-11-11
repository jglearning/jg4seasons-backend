package com.jglearning.jg4seasons.infra.audio;

import java.util.regex.Pattern;

/**
 * 음원 쿠키가 여는 범위(ADR 0031 결정 6). 정책 범위는 하나뿐이라 셋 중 하나다. 누가 어느 범위를 받는지는 호출하는 쪽(auth)이 정한다.
 *
 * <ul>
 *   <li>{@link All}: 모든 음원(설정의 Resource 그대로)
 *   <li>{@link Book}: 그 권의 음원만. 회차 폴더가 권 코드로 시작하므로 {@code /audio/<권 코드>-*}
 *   <li>{@link None}: 음원 쿠키를 주지 않는다
 * </ul>
 */
public sealed interface AudioScope {

    static AudioScope all() {
        return All.INSTANCE;
    }

    /** 권 코드(예: L2-B1). 정책 JSON·와일드카드에 그대로 들어가므로 대문자·숫자를 대시로 이은 형식만 받는다 */
    static AudioScope book(String bookCode) {
        return new Book(bookCode);
    }

    static AudioScope none() {
        return None.INSTANCE;
    }

    enum All implements AudioScope {
        INSTANCE
    }

    enum None implements AudioScope {
        INSTANCE
    }

    record Book(String code) implements AudioScope {

        private static final Pattern CODE = Pattern.compile("^[A-Z0-9]+(-[A-Z0-9]+)*$");

        public Book {
            // 메시지에 값을 넣지 않는다 — 형식이 틀린 값은 데이터 오류이고, 따옴표 등이 로그를 어지럽힌다
            if (code == null || !CODE.matcher(code).matches()) {
                throw new IllegalArgumentException("음원 범위의 권 코드는 대문자·숫자를 대시로 이은 형식이어야 한다");
            }
        }
    }
}
