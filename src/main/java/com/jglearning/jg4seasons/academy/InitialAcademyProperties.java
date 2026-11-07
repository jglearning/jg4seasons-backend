package com.jglearning.jg4seasons.academy;

import com.jglearning.jg4seasons.auth.api.StaffAccounts;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 첫 학원·관리자 설정(jg4seasons.initial.*, SYS-01). 운영 값은 환경 변수로만 넣는다(Parameter Store → 서버 환경 변수 파일).
 *
 * <p>값은 학원이 하나도 없을 때만 읽고 검사한다(resolve). 빈 문자열은 없는 값으로 본다 — 운영 설정이 {@code ${INITIAL_...:}}라 빠진 환경 변수가
 * 빈 문자열로 온다.
 *
 * @param required 값이 하나도 없으면 서버를 멈출지. 운영(prod)만 true
 */
@ConfigurationProperties("jg4seasons.initial")
record InitialAcademyProperties(
        String academyName,
        String academySlug,
        String adminLoginId,
        String adminPassword,
        String adminName,
        @DefaultValue("false") boolean required) {

    static final String PREFIX = "jg4seasons.initial";
    static final String DEFAULT_ADMIN_NAME = "관리자";
    static final int MIN_PASSWORD = 10;
    /** BCrypt는 72바이트 뒤를 버린다 */
    static final int MAX_PASSWORD_BYTES = 72;
    /** academy.name VARCHAR(50) */
    static final int MAX_ACADEMY_NAME = 50;
    /** 교사 계정 이름 규칙과 같다(StaffAccounts) */
    static final int MAX_ADMIN_NAME = 30;
    /** academy.slug VARCHAR(30). 소문자·숫자·-, 영숫자로 시작 */
    private static final Pattern SLUG = Pattern.compile("^[a-z0-9][a-z0-9-]{1,29}$");

    private static final Pattern LOGIN_ID = Pattern.compile(StaffAccounts.LOGIN_ID_REQUEST_REGEXP);

    /**
     * 검사한 값. 값이 하나도 없으면 required가 false일 때 빈 값, true면 예외. 일부만 있거나 형식이 틀리면 예외(빠진·틀린 키 이름만 싣고 값은 싣지
     * 않는다) — 아무도 로그인할 수 없는 서버가 정상인 것처럼 뜨지 않게 한다.
     */
    Optional<InitialAcademy> resolve() {
        List<String> missing = new ArrayList<>();
        if (isBlank(academyName)) {
            missing.add("academy-name");
        }
        if (isBlank(academySlug)) {
            missing.add("academy-slug");
        }
        if (isBlank(adminLoginId)) {
            missing.add("admin-login-id");
        }
        if (isBlank(adminPassword)) {
            missing.add("admin-password");
        }
        if (missing.size() == 4) {
            if (required) {
                throw new IllegalStateException("학원이 하나도 없는데 첫 학원 설정(" + PREFIX + ")이 없다. 아무도 로그인할 수 없다.");
            }
            return Optional.empty();
        }
        if (!missing.isEmpty()) {
            throw new IllegalStateException("첫 학원 설정(" + PREFIX + ")에 빠진 값이 있다: " + String.join(", ", missing));
        }

        String name = academyName.strip();
        if (name.length() > MAX_ACADEMY_NAME) {
            throw invalid("academy-name", "1~" + MAX_ACADEMY_NAME + "자");
        }
        String slug = academySlug.strip();
        if (!SLUG.matcher(slug).matches()) {
            throw invalid("academy-slug", "소문자·숫자·- 2~30자, 영숫자로 시작");
        }
        if (!LOGIN_ID.matcher(adminLoginId).matches()) {
            throw invalid("admin-login-id", StaffAccounts.LOGIN_ID_MESSAGE);
        }
        String loginId = adminLoginId.strip().toLowerCase(Locale.ROOT);
        checkPassword(loginId);
        String admin = isBlank(adminName) ? DEFAULT_ADMIN_NAME : adminName.strip();
        if (admin.length() > MAX_ADMIN_NAME) {
            throw invalid("admin-name", "1~" + MAX_ADMIN_NAME + "자");
        }
        return Optional.of(new InitialAcademy(name, slug, loginId, adminPassword, admin));
    }

    /** 교사 계정 규칙(8자)보다 길게 — 화면에서 바꾸기 전까지 이 값이 운영 관리자 비밀번호다 */
    private void checkPassword(String loginId) {
        if (adminPassword.length() < MIN_PASSWORD
                || adminPassword.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES
                || adminPassword.strip().equalsIgnoreCase(loginId)
                || adminPassword.chars().anyMatch(Character::isISOControl)) {
            throw invalid("admin-password", "10자 이상, 72바이트 이하(한글은 한 글자가 3바이트)이고 아이디와 달라야 하며 제어 문자를 쓸 수 없다");
        }
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
        return "InitialAcademyProperties[academyName=" + academyName + ", academySlug=" + academySlug
                + ", adminLoginId=" + adminLoginId + ", adminName=" + adminName + ", required=" + required + "]";
    }
}
