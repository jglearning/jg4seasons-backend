package com.jglearning.jg4seasons.content.importer;

import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

/**
 * 콘텐츠 import 실행(ADR 0027). {@code --jg4seasons.content-import.dir=<manifest 폴더>}를 주면 서버가 뜬 뒤 회차를 넣고 끝난다. 8080과 겹치지 않게
 * {@code --server.port=0}으로 띄운다(웹 계층이 없으면 접근 규칙 기동 검사가 뜨지 않는다).
 */
@Configuration
@ConditionalOnProperty(ContentImportConfig.DIR)
class ContentImportConfig {

    static final String DIR = "jg4seasons.content-import.dir";

    @Bean
    @Order(100) // 첫 학원·개발 시드 다음
    ContentImportRunner contentImportRunner(
            LessonImporter importer, @Value("${" + DIR + "}") Path dir, ConfigurableApplicationContext context) {
        return new ContentImportRunner(importer, dir, code -> System.exit(SpringApplication.exit(context, () -> code)));
    }
}
