package com.jglearning.jg4seasons.auth;

import com.jglearning.jg4seasons.auth.api.StaffAccounts;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 운영자 계정 설정(jg4seasons.initial-operator.*, ADR 0032). 운영 값은 환경 변수로만 넣는다(Parameter Store → 서버 환경 변수 파일).
 *
 * <p>운영자가 한 명도 없을 때만 읽고 검사한다(resolve). 빈 문자열은 없는 값으로 본다. 값이 하나도 없으면 만들지 않는다 — 운영자가 없어도 학원들은 돈다.
 * 일부만 있거나 형식이 틀리면 예외(키 이름만 싣고 값은 싣지 않는다) — 넣으려던 계정이 조용히 빠지지 않게.
 */
@ConfigurationProperties("jg4seasons.initial-operator")
record InitialOperatorProperties(String loginId, String password, String name) {

    static final String PREFIX = "jg4seasons.initial-operator";
    static final String DEFAULT_NAME = "운영자";
    /** 모든 학원 사용권을 바꾸는 계정 — 첫 학원 관리자와 같게 교사 계정(8자)보다 길게 */
    static final int MIN_PASSWORD = 10;
    /** BCrypt는 72바이트 뒤를 버린다 */
    static final int MAX_PASSWORD_BYTES = 72;

    static final int MAX_NAME = 30;

    private static final Pattern LOGIN_ID = Pattern.compile(StaffAccounts.LOGIN_ID_REQUEST_REGEXP);

    Optional<InitialOperator> resolve() {
        List<String> missing = new ArrayList<>();
        if (isBlank(loginId)) {
            missing.add("login-id");
        }
        if (isBlank(password)) {
            missing.add("password");
        }
        if (missing.size() == 2) {
            return Optional.empty();
        }
        if (!missing.isEmpty()) {
            throw new IllegalStateException("운영자 설정(" + PREFIX + ")에 빠진 값이 있다: " + String.join(", ", missing));
        }
        if (!LOGIN_ID.matcher(loginId).matches()) {
            throw invalid("login-id", StaffAccounts.LOGIN_ID_MESSAGE);
        }
        String normalized = loginId.strip().toLowerCase(Locale.ROOT);
        if (password.length() < MIN_PASSWORD
                || password.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES
                || password.strip().equalsIgnoreCase(normalized)
                || password.chars().anyMatch(Character::isISOControl)) {
            throw invalid("password", "10자 이상, 72바이트 이하(한글은 한 글자가 3바이트)이고 아이디와 달라야 하며 제어 문자를 쓸 수 없다");
        }
        String operatorName = isBlank(name) ? DEFAULT_NAME : name.strip();
        if (operatorName.length() > MAX_NAME) {
            throw invalid("name", "1~" + MAX_NAME + "자");
        }
        return Optional.of(new InitialOperator(normalized, password, operatorName));
    }

    private static IllegalStateException invalid(String key, String rule) {
        return new IllegalStateException(PREFIX + "." + key + " 형식이 틀렸다: " + rule);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /** 비밀번호가 로그·예외 메시지에 찍히지 않게 */
    @Override
    public String toString() {
        return "InitialOperatorProperties[loginId=" + loginId + ", name=" + name + "]";
    }
}
