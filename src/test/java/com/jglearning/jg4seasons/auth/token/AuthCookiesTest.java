package com.jglearning.jg4seasons.auth.token;

import static org.assertj.core.api.Assertions.assertThat;

import com.jglearning.jg4seasons.infra.audio.AudioCookies;
import com.jglearning.jg4seasons.infra.audio.AudioScope;
import com.jglearning.jg4seasons.infra.audio.NoAudioCookies;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseCookie;

/** 쿠키 3개의 이름·경로·수명·속성(API 명세 §0.1, ADR-18). */
class AuthCookiesTest {

    static final IssuedTokens TOKENS =
            new IssuedTokens("jwt-value", "refresh-value", "csrf-value", Duration.ofDays(90), AudioScope.all());

    static Map<String, ResponseCookie> byName(List<ResponseCookie> cookies) {
        return cookies.stream().collect(Collectors.toMap(ResponseCookie::getName, Function.identity()));
    }

    @Test
    void 발급_쿠키_3개의_속성() {
        Map<String, ResponseCookie> cookies = byName(new AuthCookies(true).issue(TOKENS));

        assertThat(cookies).containsOnlyKeys("access", "refresh", "csrf");

        ResponseCookie access = cookies.get("access");
        assertThat(access.getValue()).isEqualTo("jwt-value");
        assertThat(access.getPath()).isEqualTo("/api");
        assertThat(access.getMaxAge()).isEqualTo(Duration.ofSeconds(1800));
        assertThat(access.isHttpOnly()).isTrue();

        ResponseCookie refresh = cookies.get("refresh");
        assertThat(refresh.getValue()).isEqualTo("refresh-value");
        assertThat(refresh.getPath()).isEqualTo("/api/v1/auth");
        assertThat(refresh.getMaxAge()).isEqualTo(Duration.ofSeconds(7_776_000));
        assertThat(refresh.isHttpOnly()).isTrue();

        ResponseCookie csrf = cookies.get("csrf");
        assertThat(csrf.getValue()).isEqualTo("csrf-value");
        assertThat(csrf.getPath()).isEqualTo("/");
        assertThat(csrf.getMaxAge()).isEqualTo(Duration.ofSeconds(7_776_000));
        assertThat(csrf.isHttpOnly()).as("JS가 읽는다").isFalse();

        for (ResponseCookie cookie : cookies.values()) {
            assertThat(cookie.getSameSite()).isEqualTo("Lax");
            assertThat(cookie.isSecure()).isTrue();
            assertThat(cookie.getDomain()).as("호스트 전용").isNull();
        }
    }

    @Test
    void 로컬은_Secure를_끈다() {
        assertThat(new AuthCookies(false).issue(TOKENS)).noneMatch(ResponseCookie::isSecure);
    }

    @Test
    void 만료_쿠키는_같은_경로에_수명_0이다() {
        Map<String, ResponseCookie> expired = byName(new AuthCookies(true).expireAll());

        assertThat(expired).containsOnlyKeys("access", "refresh", "csrf");
        assertThat(expired.get("access").getPath()).isEqualTo("/api");
        assertThat(expired.get("refresh").getPath()).isEqualTo("/api/v1/auth");
        assertThat(expired.get("csrf").getPath()).isEqualTo("/");
        assertThat(expired.values()).allSatisfy(cookie -> {
            assertThat(cookie.getMaxAge()).isEqualTo(Duration.ZERO);
            assertThat(cookie.getValue()).isEmpty();
        });
    }

    /** 서명 켜짐을 흉내 낸다(값은 서명기가 정하므로 여기서는 이름·값만). 정책 값에 받은 범위를 적는다. none이면 서명기처럼 빈 맵 */
    static final AudioCookies SIGNED = new AudioCookies() {
        @Override
        public Map<String, String> issue(AudioScope scope) {
            if (scope instanceof AudioScope.None) {
                return Map.of();
            }
            Map<String, String> cookies = new LinkedHashMap<>();
            cookies.put("CloudFront-Policy", scope.toString());
            cookies.put("CloudFront-Signature", "s");
            cookies.put("CloudFront-Key-Pair-Id", "k");
            return cookies;
        }

        @Override
        public Set<String> names() {
            return Set.of("CloudFront-Policy", "CloudFront-Signature", "CloudFront-Key-Pair-Id");
        }

        @Override
        public Duration ttl() {
            return Duration.ofHours(2);
        }
    };

    @Test
    void 서명이_켜지면_로그인_쿠키에_음원_쿠키_셋이_붙는다() {
        Map<String, ResponseCookie> cookies = byName(new AuthCookies(true, SIGNED).issue(TOKENS));

        assertThat(cookies)
                .containsOnlyKeys(
                        "access",
                        "refresh",
                        "csrf",
                        "CloudFront-Policy",
                        "CloudFront-Signature",
                        "CloudFront-Key-Pair-Id");
        assertThat(cookies.get("CloudFront-Signature").getValue()).isEqualTo("s");
        for (String name : SIGNED.names()) {
            ResponseCookie audio = cookies.get(name);
            assertThat(audio.getPath()).isEqualTo("/audio/");
            assertThat(audio.getMaxAge()).isEqualTo(Duration.ofSeconds(7200));
            assertThat(audio.isHttpOnly()).isTrue();
            assertThat(audio.isSecure()).isTrue();
            assertThat(audio.getSameSite()).isEqualTo("Lax");
            assertThat(audio.getDomain()).isNull();
        }
        assertThat(cookies.get("access").getPath()).isEqualTo("/api");
    }

    @Test
    void 꺼져_있으면_쿠키_셋만() {
        assertThat(new AuthCookies(true, NoAudioCookies.INSTANCE).issue(TOKENS)).hasSize(3);
        assertThat(new AuthCookies(true, NoAudioCookies.INSTANCE).expireAll()).hasSize(3);
    }

    @Test
    void 로그아웃은_음원_쿠키도_Path_audio로_지운다() {
        Map<String, ResponseCookie> expired = byName(new AuthCookies(true, SIGNED).expireAll());

        assertThat(expired).hasSize(6);
        for (String name : SIGNED.names()) {
            assertThat(expired.get(name).getPath()).isEqualTo("/audio/");
            assertThat(expired.get(name).getMaxAge()).isEqualTo(Duration.ZERO);
            assertThat(expired.get(name).getValue()).isEmpty();
        }
    }

    private static IssuedTokens tokens(AudioScope scope) {
        return new IssuedTokens("jwt-value", "refresh-value", "csrf-value", Duration.ofDays(90), scope);
    }

    @Test
    void 토큰_묶음의_음원_범위로_서명한다() {
        Map<String, ResponseCookie> cookies =
                byName(new AuthCookies(true, SIGNED).issue(tokens(AudioScope.book("L2-B1"))));

        assertThat(cookies.get("CloudFront-Policy").getValue())
                .isEqualTo(AudioScope.book("L2-B1").toString());
        assertThat(cookies.get("CloudFront-Policy").getMaxAge()).isEqualTo(Duration.ofSeconds(7200));
    }

    @Test
    void 범위가_없음이면_남아_있던_음원_쿠키를_지운다() {
        List<ResponseCookie> issued = new AuthCookies(true, SIGNED).issue(tokens(AudioScope.none()));
        Map<String, ResponseCookie> cookies = byName(issued);

        assertThat(issued).hasSize(6);
        assertThat(cookies.get("access").getValue()).isEqualTo("jwt-value");
        for (String name : SIGNED.names()) {
            assertThat(cookies.get(name).getPath()).isEqualTo("/audio/");
            assertThat(cookies.get(name).getMaxAge()).isEqualTo(Duration.ZERO);
            assertThat(cookies.get(name).getValue()).isEmpty();
        }
    }

    @Test
    void 꺼져_있으면_범위가_없음이어도_쿠키_셋만() {
        assertThat(new AuthCookies(true, NoAudioCookies.INSTANCE).issue(tokens(AudioScope.none())))
                .hasSize(3);
    }
}
