package com.jglearning.jg4seasons.auth.token;

import com.jglearning.jg4seasons.auth.AuthProperties;
import com.jglearning.jg4seasons.infra.audio.AudioCookies;
import com.jglearning.jg4seasons.infra.audio.AudioScope;
import com.jglearning.jg4seasons.infra.audio.NoAudioCookies;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * 인증 쿠키 3개(API 명세 §0.1, ADR-18). 모두 SameSite=Lax, Domain 없음(호스트 전용), Secure는 설정값(로컬 http만 끔).
 *
 * <ul>
 *   <li>access: JWT, Path=/api, 30분, HttpOnly
 *   <li>refresh: 난수, Path=/api/v1/auth, 학생 90일·교사 14일, HttpOnly
 *   <li>csrf: 난수, Path=/, refresh와 같은 수명, JS가 읽음(HttpOnly 아님) — 상태 변경 요청의 헤더 X-CSRF 값
 *   <li>음원 서명 쿠키 3개(CloudFront-Policy·Signature·Key-Pair-Id, ADR 0028): Path=/audio/, 2시간, HttpOnly. 서명을 켠 운영에서만. 브라우저가
 *       음원 요청에 붙이고 CloudFront가 확인한다
 * </ul>
 */
@Component
public class AuthCookies {

    public static final String ACCESS = "access";
    public static final String REFRESH = "refresh";
    public static final String CSRF = "csrf";

    static final String ACCESS_PATH = "/api";
    static final String REFRESH_PATH = "/api/v1/auth";
    static final String CSRF_PATH = "/";
    static final String AUDIO_PATH = "/audio/";

    private final boolean secure;
    private final AudioCookies audio;

    @Autowired
    public AuthCookies(AuthProperties properties, AudioCookies audio) {
        this(properties.cookieSecure(), audio);
    }

    AuthCookies(boolean secure) {
        this(secure, NoAudioCookies.INSTANCE);
    }

    AuthCookies(boolean secure, AudioCookies audio) {
        this.secure = secure;
        this.audio = audio;
    }

    /** 로그인·refresh: 인증 쿠키 3개 + (서명을 켰으면) 묶음의 범위로 음원 쿠키 3개. 범위가 없음이면 음원 쿠키 3개를 만료 */
    public List<ResponseCookie> issue(IssuedTokens tokens) {
        List<ResponseCookie> cookies = new ArrayList<>(List.of(
                cookie(ACCESS, tokens.access(), ACCESS_PATH, SessionTtl.ACCESS, true),
                cookie(REFRESH, tokens.refresh(), REFRESH_PATH, tokens.refreshTtl(), true),
                cookie(CSRF, tokens.csrf(), CSRF_PATH, tokens.refreshTtl(), false)));
        audio.issue(tokens.audioScope())
                .forEach((name, value) -> cookies.add(cookie(name, value, AUDIO_PATH, audio.ttl(), true)));
        if (tokens.audioScope() instanceof AudioScope.None) {
            // 범위가 없으면 앞서 받은 넓은 쿠키(예: 사용권이 끝나기 전 것)가 기한까지 남지 않게 지운다
            audio.names().forEach(name -> cookies.add(cookie(name, "", AUDIO_PATH, Duration.ZERO, true)));
        }
        return List.copyOf(cookies);
    }

    /** 로그아웃·refresh 실패: 같은 경로에 수명 0(경로가 다르면 브라우저가 지우지 않는다) */
    public List<ResponseCookie> expireAll() {
        List<ResponseCookie> cookies = new ArrayList<>(List.of(
                cookie(ACCESS, "", ACCESS_PATH, Duration.ZERO, true),
                cookie(REFRESH, "", REFRESH_PATH, Duration.ZERO, true),
                cookie(CSRF, "", CSRF_PATH, Duration.ZERO, false)));
        audio.names().forEach(name -> cookies.add(cookie(name, "", AUDIO_PATH, Duration.ZERO, true)));
        return List.copyOf(cookies);
    }

    private ResponseCookie cookie(String name, String value, String path, Duration maxAge, boolean httpOnly) {
        return ResponseCookie.from(name, value)
                .path(path)
                .maxAge(maxAge)
                .httpOnly(httpOnly)
                .secure(secure)
                .sameSite("Lax")
                .build();
    }
}
