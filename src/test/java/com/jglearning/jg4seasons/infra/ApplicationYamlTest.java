package com.jglearning.jg4seasons.infra;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.FileSystemResource;

/** 운영 기본 설정(main application.yaml)의 값. 프로파일 없이 뜨는 운영 서버가 이 값을 쓴다. */
class ApplicationYamlTest {

    private static Properties mainApplicationYaml() {
        YamlPropertiesFactoryBean factory = new YamlPropertiesFactoryBean();
        // 클래스패스로 읽으면 나중에 생길 test 쪽 application.yaml이 먼저 잡힌다. 운영 파일을 경로로 직접 읽는다
        factory.setResources(new FileSystemResource("src/main/resources/application.yaml"));
        return factory.getObject();
    }

    @Test
    void 운영_기본값은_api_docs를_끈다() {
        assertThat(mainApplicationYaml().getProperty("springdoc.api-docs.enabled"))
                .isEqualTo("false");
    }

    @Test
    void 운영_JDBC_시간대는_UTC다() {
        // 서버 기본 시간대(KST)로 저장되면 시점 비교와 날짜 판정이 9시간 어긋난다
        assertThat(mainApplicationYaml().getProperty("spring.jpa.properties.hibernate.jdbc.time_zone"))
                .isEqualTo("UTC");
    }

    @Test
    void 운영_설정의_비밀값은_기본값_없는_환경_변수_자리뿐이다() {
        // 기본값이 있으면 환경 변수를 빠뜨린 운영 서버가 그 값으로 뜬다. 비어 있으면 AuthProperties가 기동을 막는다
        Properties yaml = mainApplicationYaml();
        assertThat(yaml.getProperty("jg4seasons.auth.personal-code-pepper"))
                .isEqualTo("${JG4SEASONS_PERSONAL_CODE_PEPPER:}");
        assertThat(yaml.getProperty("jg4seasons.auth.jwt-secret")).isEqualTo("${JG4SEASONS_JWT_SECRET:}");
    }

    @Test
    void 스키마는_Flyway가_만들고_JPA는_검증만_한다() {
        assertThat(mainApplicationYaml().getProperty("spring.jpa.hibernate.ddl-auto"))
                .isEqualTo("validate");
    }
}
