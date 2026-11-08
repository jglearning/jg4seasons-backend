package com.jglearning.jg4seasons.infra.persistence.devseed;

import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

/**
 * 시험 시드가 켜진 프로파일을 가입 코드 검사(SeedAccessCode)에 그대로 넘기는지. trial인데 local로 넘기면 빈 값이 공개 코드로 조용히 바뀌어 저장소를 본
 * 사람이 운영 데모 반에 들어온다.
 */
class DevSeedRunnerTest {

    private static DevSeedRunner runner(String accessCode, String... profiles) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(profiles);
        return new DevSeedRunner(null, Clock.systemUTC(), environment, accessCode);
    }

    @Test
    void 운영_시험에서_가입_코드가_비었으면_서버_시작을_멈춘다() {
        assertThatIllegalStateException().isThrownBy(() -> runner("", "prod", "trial"));
    }

    @Test
    void 운영_시험에서_공개_가입_코드는_쓸_수_없다() {
        assertThatIllegalStateException().isThrownBy(() -> runner(SeedAccessCode.PUBLIC_CODE, "prod", "trial"));
    }

    @Test
    void 로컬은_가입_코드가_없어도_뜬다() {
        assertThatNoException().isThrownBy(() -> runner("", "local"));
    }
}
