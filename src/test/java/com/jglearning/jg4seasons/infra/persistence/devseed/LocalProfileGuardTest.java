package com.jglearning.jg4seasons.infra.persistence.devseed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatNoException;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Profile;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.stereotype.Component;

/**
 * local 프로파일은 공개 저장소의 비밀번호·가입 코드로 계정을 만든다. 운영(prod)에 함께 켜지면(파라미터 SPRING_PROFILES_INCLUDE=local 실수 등) 서버가
 * 뜨지 않아야 한다.
 */
class LocalProfileGuardTest {

    private static MockEnvironment profiles(String... active) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(active);
        return environment;
    }

    @Test
    void 운영과_local이_함께_켜지면_서버가_뜨지_않는다() {
        assertThatIllegalStateException()
                .isThrownBy(() -> new LocalProfileGuard(profiles("prod", "local")))
                .withMessageContaining("local");
        assertThatIllegalStateException().isThrownBy(() -> new LocalProfileGuard(profiles("prod", "trial", "local")));
    }

    @Test
    void local만이면_그대로_뜬다() {
        assertThatNoException().isThrownBy(() -> new LocalProfileGuard(profiles("local")));
    }

    // local이 켜졌을 때만 만들어져야 검사가 local 시드보다 먼저, local일 때 반드시 돈다
    @Test
    void local_프로파일의_빈이다() {
        assertThat(LocalProfileGuard.class.getAnnotation(Profile.class).value()).containsExactly("local");
        assertThat(LocalProfileGuard.class.isAnnotationPresent(Component.class)).isTrue();
    }
}
