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
 *
 * <p>서명기는 {@link Signed}(전체·권)만 받는다 — none을 서명하는 실수는 컴파일되지 않는다.
 */
public sealed interface AudioScope {

    static Signed all() {
        return All.INSTANCE;
    }

    /**
     * 권 코드(예: L2-B1, K-B1 — 기준 데이터의 {@code <레벨>-B<시즌>}). 정책 JSON·와일드카드에 그대로 들어가므로 그 형식만 받는다: 레벨만(L2 → L2-* = 네 권)이나 회차
     * 코드(L2-B1-05 → 아무 음원도 맞지 않음)가 들어오면 범위가 "권 하나"가 아니게 된다
     */
    static Signed book(String bookCode) {
        return new Book(bookCode);
    }

    static AudioScope none() {
        return None.INSTANCE;
    }

    /** 서명할 범위(전체·권). none은 여기 들지 않는다 */
    sealed interface Signed extends AudioScope {}

    enum All implements Signed {
        INSTANCE
    }

    enum None implements AudioScope {
        INSTANCE
    }

    record Book(String code) implements Signed {

        private static final Pattern CODE = Pattern.compile("^[A-Z0-9]+-B[0-9]+$");

        public Book {
            // 메시지에 값을 넣지 않는다 — 형식이 틀린 값은 데이터 오류이고, 따옴표 등이 로그를 어지럽힌다
            if (code == null || !CODE.matcher(code).matches()) {
                throw new IllegalArgumentException("음원 범위의 권 코드는 <레벨>-B<번호> 형식이어야 한다");
            }
        }
    }
}
