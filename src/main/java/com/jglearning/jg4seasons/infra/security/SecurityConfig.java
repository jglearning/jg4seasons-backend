package com.jglearning.jg4seasons.infra.security;

import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 임시 최소 보안 설정. 인증 이슈(쿠키 JWT·CSRF)에서 교체한다.
 *
 * <p>명세(/v3/api-docs)와 헬스 체크만 열고 나머지는 인증을 요구한다. 명세는 운영에서 springdoc 설정으로 꺼진다. 막힌 요청은 401·403
 * Problem Details로 응답한다(인증 방식과 무관하게 유지).
 */
@Configuration(proxyBeanMethods = false)
public class SecurityConfig {

    private static final String CODE_VERIFY = "/api/v1/auth/code/verify";

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            ProblemAuthenticationEntryPoint authenticationEntryPoint,
            ProblemAccessDeniedHandler accessDeniedHandler)
            throws Exception {
        http.authorizeHttpRequests(auth -> auth
                        // MVC 밖 오류는 ERROR 디스패치로 /error에 온다. 막으면 원래 400·500이 401로 바뀐다
                        .dispatcherTypeMatchers(DispatcherType.ERROR)
                        .permitAll()
                        .requestMatchers("/error")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/v3/api-docs", "/v3/api-docs/**")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/actuator/health")
                        .permitAll()
                        // 로그인 전 공개 경로(03 §1.1). 쿠키 JWT·메서드 보안으로 바꿀 때(이슈 #9 Task 6) 정리한다
                        .requestMatchers(HttpMethod.POST, CODE_VERIFY)
                        .permitAll()
                        .anyRequest()
                        .authenticated())
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                // 로그인 전 요청은 CSRF 검사 제외(03 §0.2)
                .csrf(csrf -> csrf.ignoringRequestMatchers(CODE_VERIFY))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler));
        return http.build();
    }
}
