package com.jglearning.jg4seasons.infra.persistence.devseed;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 시드 반의 가입 코드. local은 값이 없으면 공개 값(G7KM2XP4)을 쓴다. 운영 시험(trial)은 설정값(Parameter Store)만 받는다 — 비었거나 공개 값이거나
 * 형식이 틀리면 서버가 뜨지 않는다. 메시지에 값은 싣지 않는다.
 */
final class SeedAccessCode {

    static final String KEY = "jg4seasons.seed.access-code";
    /** 공개 저장소에 있는 로컬 전용 값 */
    static final String PUBLIC_CODE = DevSeed.ACCESS_CODE;
    /** 가입 코드 생성 규칙(clazz AccessCodeGenerator)과 같은 글자: 헷갈리는 I·L·O·0·1 없음, 8자 */
    private static final Pattern FORMAT = Pattern.compile("^[ABCDEFGHJKMNPQRSTUVWXYZ2-9]{8}$");

    private SeedAccessCode() {}

    static String resolve(String configured, boolean trial) {
        if (configured == null || configured.isBlank()) {
            if (trial) {
                throw new IllegalStateException(
                        "trial 프로파일에는 " + KEY + "가 필요하다(JG4SEASONS_TRIAL_ACCESS_CODE, Parameter Store)");
            }
            return PUBLIC_CODE;
        }
        String code = configured.strip().toUpperCase(Locale.ROOT);
        if (!FORMAT.matcher(code).matches()) {
            throw new IllegalStateException(KEY + " 형식이 틀렸다: 8자, 영문 대문자(I·L·O 제외)·숫자 2~9");
        }
        if (trial && code.equals(PUBLIC_CODE)) {
            throw new IllegalStateException(KEY + "가 공개 저장소의 값이다. trial에는 Parameter Store의 다른 값을 쓴다");
        }
        return code;
    }
}
