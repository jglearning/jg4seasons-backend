package com.jglearning.jg4seasons.infra;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.FileSystemResource;

/** 운영 프로파일(application-prod.yaml)의 값. 운영 서버는 SPRING_PROFILES_ACTIVE=prod로 뜬다. */
class ProdProfileYamlTest {

    private static Properties prodYaml() {
        YamlPropertiesFactoryBean factory = new YamlPropertiesFactoryBean();
        factory.setResources(new FileSystemResource("src/main/resources/application-prod.yaml"));
        return factory.getObject();
    }

    @Test
    void DB_접속값은_기본값_없는_환경_변수_자리다() {
        // 기본값이 있으면 파라미터를 빠뜨린 서버가 엉뚱한 DB로 뜬다. 없으면 서버가 뜨지 않는다
        Properties yaml = prodYaml();
        assertThat(yaml.getProperty("spring.datasource.url")).isEqualTo("${DB_URL}");
        assertThat(yaml.getProperty("spring.datasource.username")).isEqualTo("${DB_USERNAME}");
        assertThat(yaml.getProperty("spring.datasource.password")).isEqualTo("${DB_PASSWORD}");
    }

    @Test
    void 쿠키는_Secure로_명시한다() {
        assertThat(prodYaml().getProperty("jg4seasons.auth.cookie-secure")).isEqualTo("true");
    }

    @Test
    void 클라이언트_IP는_Nginx가_덮어쓰는_X_Forwarded_For에서_읽는다() {
        // Nginx가 CloudFront-Viewer-Address에서 포트를 뗀 IP 하나로 덮어쓴다. 앱 포트는 서버 안(127.0.0.1)에만 열린다
        assertThat(prodYaml().getProperty("jg4seasons.auth.client-ip-header")).isEqualTo("X-Forwarded-For");
    }

    @Test
    void API_문서는_운영에서_끈다() {
        assertThat(prodYaml().getProperty("springdoc.api-docs.enabled")).isEqualTo("false");
    }
}
