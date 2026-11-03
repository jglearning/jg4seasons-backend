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
}
