package com.jglearning.jg4seasons.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** 비밀값(pepper·JWT 키)이 없거나 짧으면 서버가 뜨지 않는다. 오류 메시지에 값이 나오지 않는다. */
class AuthPropertiesTest {

    static final String PEPPER = "test-pepper-0123456789abcdef0123456789";
    static final String JWT_SECRET = "test-jwt-secret-0123456789abcdef0123";

    final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(AuthConfig.class);

    @Test
    void 비밀값이_있으면_뜨고_기본값이_채워진다() {
        runner.withPropertyValues(
                        "jg4seasons.auth.personal-code-pepper=" + PEPPER, "jg4seasons.auth.jwt-secret=" + JWT_SECRET)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    AuthProperties properties = context.getBean(AuthProperties.class);
                    assertThat(properties.cookieSecure()).isTrue();
                    assertThat(properties.clientIpHeader()).isNull();
                    assertThat(properties.refreshReuseGrace()).isEqualTo(Duration.ofSeconds(10));
                });
    }

    @Test
    void pepper가_없으면_기동하지_않는다() {
        runner.withPropertyValues("jg4seasons.auth.jwt-secret=" + JWT_SECRET)
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void pepper가_빈_값이면_기동하지_않는다() {
        runner.withPropertyValues("jg4seasons.auth.personal-code-pepper=", "jg4seasons.auth.jwt-secret=" + JWT_SECRET)
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void pepper가_32바이트_미만이면_기동하지_않고_값을_드러내지_않는다() {
        String shortPepper = "short-pepper-31-bytes-xxxxxxxxx";
        runner.withPropertyValues(
                        "jg4seasons.auth.personal-code-pepper=" + shortPepper,
                        "jg4seasons.auth.jwt-secret=" + JWT_SECRET)
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(fullMessage(context.getStartupFailure())).doesNotContain(shortPepper);
                });
    }

    @Test
    void jwt_secret이_없으면_기동하지_않는다() {
        runner.withPropertyValues("jg4seasons.auth.personal-code-pepper=" + PEPPER)
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void jwt_secret이_32바이트_미만이면_기동하지_않는다() {
        runner.withPropertyValues(
                        "jg4seasons.auth.personal-code-pepper=" + PEPPER, "jg4seasons.auth.jwt-secret=too-short")
                .run(context -> assertThat(context).hasFailed());
    }

    private static String fullMessage(Throwable failure) {
        StringBuilder text = new StringBuilder();
        for (Throwable t = failure; t != null; t = t.getCause()) {
            text.append(t).append('\n');
        }
        return text.toString();
    }
}
