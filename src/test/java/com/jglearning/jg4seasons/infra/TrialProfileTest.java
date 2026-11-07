package com.jglearning.jg4seasons.infra;

import static org.assertj.core.api.Assertions.assertThat;

import com.jglearning.jg4seasons.auth.devseed.AuthDevSeedRunner;
import com.jglearning.jg4seasons.auth.devseed.StaffDevSeedRunner;
import com.jglearning.jg4seasons.infra.persistence.devseed.DevSeedRunner;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.FileSystemResource;

/**
 * 운영 시험 데이터 프로파일 trial(운영 prod에 SPRING_PROFILES_INCLUDE=trial로 더한다 — 이슈 #23 결정 I·J). 데모 학원·반·학생·진도표·회차 내용과 학생 개인 코드만
 * 넣고, 공개 비밀번호를 쓰는 교사·관리자 로그인 시드는 넣지 않는다. 테스트 컨텍스트를 하나로 유지하려고 프로파일을 켜지 않고 선언을 본다.
 */
class TrialProfileTest {

    private static String[] profiles(Class<?> type) {
        return type.getAnnotation(Profile.class).value();
    }

    @Test
    void 데모_데이터와_학생_개인_코드_시드는_local과_trial에서_돈다() {
        assertThat(profiles(DevSeedRunner.class)).containsExactlyInAnyOrder("local", "trial");
        assertThat(profiles(AuthDevSeedRunner.class)).containsExactlyInAnyOrder("local", "trial");
    }

    // 저장소에 평문으로 있는 비밀번호(local-admin-1234 등)로 운영 계정이 생기면 안 된다
    @Test
    void 교사_관리자_로그인_시드는_local에서만_돈다() {
        assertThat(profiles(StaffDevSeedRunner.class)).containsExactly("local");
    }

    @Test
    void trial의_가입_코드는_기본값_없는_환경_변수에서_받는다() {
        YamlPropertiesFactoryBean factory = new YamlPropertiesFactoryBean();
        factory.setResources(new FileSystemResource("src/main/resources/application-trial.yaml"));
        Properties yaml = factory.getObject();

        // 빈 값으로 오면 SeedAccessCode가 서버 시작을 멈춘다
        assertThat(yaml.getProperty("jg4seasons.seed.access-code")).isEqualTo("${JG4SEASONS_TRIAL_ACCESS_CODE:}");
    }
}
