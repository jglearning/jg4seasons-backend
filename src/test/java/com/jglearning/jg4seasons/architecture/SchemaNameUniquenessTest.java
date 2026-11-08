package com.jglearning.jg4seasons.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * openapi 스키마 이름이 겹치지 않는다. springdoc은 스키마 이름으로 클래스 단순 이름(또는 클래스의 @Schema(name))을 쓰고, 이름이 겹치면 한쪽 정의가 경고 없이
 * 다른 쪽을 덮는다(예: 교사·학생 로그인 응답의 UserInfo). 생성본 비교 테스트는 생성 결과끼리만 비교해 이것을 잡지 못한다.
 */
class SchemaNameUniquenessTest {

    @Test
    void 웹_DTO의_스키마_이름은_겹치지_않는다() {
        List<JavaClass> dtos = new ClassFileImporter()
                        .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                        .importPackages("com.jglearning.jg4seasons")
                        .stream()
                        .filter(c -> c.getPackageName().contains(".web.dto"))
                        .filter(c -> c.isRecord() || c.isEnum())
                        .toList();

        Map<String, List<String>> byName = dtos.stream()
                .collect(Collectors.groupingBy(
                        SchemaNameUniquenessTest::schemaName,
                        TreeMap::new,
                        Collectors.mapping(JavaClass::getName, Collectors.toList())));

        assertThat(dtos).as("검사한 DTO가 없다 — 패키지 이름이 바뀌었는지 확인").isNotEmpty();
        assertThat(byName)
                .as("같은 스키마 이름을 쓰는 DTO(@Schema(name = ...)으로 구분)")
                .allSatisfy((name, classes) -> assertThat(classes).as(name).hasSize(1));
    }

    private static String schemaName(JavaClass type) {
        if (type.isAnnotatedWith(Schema.class)) {
            String name = type.getAnnotationOfType(Schema.class).name();
            if (!name.isBlank()) {
                return name;
            }
        }
        return type.getSimpleName();
    }
}
