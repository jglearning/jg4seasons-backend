package com.jglearning.jg4seasons.infra.security;

import jakarta.servlet.DispatcherType;
import java.util.List;
import java.util.Locale;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationProvider;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.authentication.AuthenticationEntryPointFailureHandler;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

/**
 * 쿠키 JWT 인증 · X-CSRF 검사 · 역할(메서드 보안 JSR-250). ADR-18.
 *
 * <ul>
 *   <li>URL 층: 공개 API(PublicEndpoints)·명세·헬스 체크·/error만 열고 나머지는 인증 필요. 역할은 메서드의 @RolesAllowed가 정한다
 *   <li>access JWT는 쿠키에서만(CookieBearerTokenResolver). JWT claim role → 권한 ROLE_대문자(예: ROLE_STUDENT)
 *   <li>CSRF: 쿠키 csrf = 헤더 X-CSRF(CsrfCookieReader, 마스킹 없음). 로그인·코드 확인·refresh는 제외
 *   <li>세션 없음(STATELESS). 막힌 요청은 401·403 Problem Details
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity(jsr250Enabled = true)
public class SecurityConfig {

    static final String ROLE_CLAIM = "role";

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            ProblemAuthenticationEntryPoint authenticationEntryPoint,
            ProblemAccessDeniedHandler accessDeniedHandler,
            JwtDecoder jwtDecoder)
            throws Exception {
        http.authorizeHttpRequests(auth -> auth
                        // MVC 밖 오류는 ERROR 디스패치로 /error에 온다. 막으면 원래 400·500이 401로 바뀐다
                        .dispatcherTypeMatchers(DispatcherType.ERROR)
                        .permitAll()
                        .requestMatchers("/error")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/v3/api-docs", "/v3/api-docs/**")
                        .permitAll()
                        // readiness·liveness(배포 스크립트·Nginx /api/health). 다른 actuator는 노출하지 않는다
                        .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**")
                        .permitAll()
                        .requestMatchers(HttpMethod.POST, PublicEndpoints.NO_AUTH.toArray(String[]::new))
                        .permitAll()
                        .anyRequest()
                        .authenticated())
                // oauth2ResourceServer()는 쓰지 않는다: 토큰이 있는 요청을 CSRF 검사에서 빼는 규칙을 스스로 넣는데, 토큰이 쿠키에 있으면
                // 브라우저가 자동으로 실어 CSRF가 그대로 열린다. 같은 필터를 직접 등록한다
                .addFilterBefore(
                        accessCookieFilter(jwtDecoder, authenticationEntryPoint), AnonymousAuthenticationFilter.class)
                .csrf(csrf -> csrf.csrfTokenRepository(new CsrfCookieReader())
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                        .ignoringRequestMatchers(PublicEndpoints.NO_CSRF.toArray(String[]::new)))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler));
        return http.build();
    }

    /**
     * 역할 계층(API 명세 §0.1, ADR 0032): admin > teacher. student·superadmin(운영자 — 학원 없음)은 계층 밖이라 학원 API를 통과하지 않는다.
     * @RolesAllowed("TEACHER")면 admin도 통과. static —
     * 메서드 보안 설정보다 먼저 만들어지게
     */
    @Bean
    static RoleHierarchy roleHierarchy() {
        return RoleHierarchyImpl.withDefaultRolePrefix()
                .role("ADMIN")
                .implies("TEACHER")
                .build();
    }

    /** 쿠키 access JWT 인증. 실패(만료·서명 불일치·형식)는 401 Problem Details */
    static BearerTokenAuthenticationFilter accessCookieFilter(
            JwtDecoder jwtDecoder, ProblemAuthenticationEntryPoint authenticationEntryPoint) {
        JwtAuthenticationProvider provider = new JwtAuthenticationProvider(jwtDecoder);
        provider.setJwtAuthenticationConverter(roleConverter());
        BearerTokenAuthenticationFilter filter = new BearerTokenAuthenticationFilter(new ProviderManager(provider));
        filter.setBearerTokenResolver(new CookieBearerTokenResolver());
        filter.setAuthenticationFailureHandler(new AuthenticationEntryPointFailureHandler(authenticationEntryPoint));
        return filter;
    }

    /** JWT role(소문자, 예: student) → 권한 ROLE_STUDENT. principal 이름은 sub(user id) */
    static JwtAuthenticationConverter roleConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            String role = jwt.getClaimAsString(ROLE_CLAIM);
            return role == null
                    ? List.of()
                    : List.of(new SimpleGrantedAuthority("ROLE_" + role.toUpperCase(Locale.ROOT)));
        });
        return converter;
    }
}
