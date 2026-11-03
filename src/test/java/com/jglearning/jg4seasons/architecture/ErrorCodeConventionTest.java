package com.jglearning.jg4seasons.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.jglearning.archfixture.errors.BadErrorCode;
import com.jglearning.jg4seasons.infra.web.error.CommonErrorCode;
import com.jglearning.jg4seasons.infra.web.error.ErrorCode;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * 모듈별 오류 code enum의 규칙: code는 대문자·숫자·밑줄, HTTP 상태는 4xx·5xx, 모든 모듈을 통틀어 code가 겹치지 않는다(프론트는 code 하나로 분기한다).
 */
class ErrorCodeConventionTest {

    private static final Pattern CODE = Pattern.compile("^[A-Z][A-Z0-9_]*$");

    /** 운영 코드에서 ErrorCode를 구현한 enum 전부 */
    static List<Class<?>> productionErrorCodeEnums() {
        List<Class<?>> enums = new ArrayList<>();
        for (JavaClass javaClass : new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(ArchitectureTest.BASE)) {
            if (javaClass.isEnum() && javaClass.isAssignableTo(ErrorCode.class)) {
                enums.add(javaClass.reflect());
            }
        }
        return enums;
    }

    static List<String> violations(List<Class<?>> enumTypes) {
        List<String> violations = new ArrayList<>();
        Map<String, String> owners = new HashMap<>();
        for (Class<?> type : enumTypes) {
            for (Object constant : type.getEnumConstants()) {
                ErrorCode errorCode = (ErrorCode) constant;
                String where = type.getSimpleName() + "." + errorCode.code();
                if (!CODE.matcher(errorCode.code()).matches()) {
                    violations.add(where + ": code 형식(대문자·숫자·밑줄)이 아니다");
                }
                if (!errorCode.status().isError()) {
                    violations.add(where + ": 오류 상태(4xx·5xx)가 아니다");
                }
                String owner = owners.putIfAbsent(errorCode.code(), type.getSimpleName());
                if (owner != null) {
                    violations.add(where + ": " + owner + "와 code가 겹친다");
                }
            }
        }
        return violations;
    }

    @Test
    void 운영_코드의_오류_code는_형식이_맞고_겹치지_않는다() {
        List<Class<?>> enums = productionErrorCodeEnums();
        assertThat(enums).contains(CommonErrorCode.class);
        assertThat(violations(enums)).isEmpty();
    }

    @Test
    void 위반_예제의_소문자_상태_중복을_잡는다() {
        assertThat(violations(List.of(CommonErrorCode.class, BadErrorCode.class)))
                .anyMatch(v -> v.contains("lower_case") && v.contains("형식"))
                .anyMatch(v -> v.contains("NOT_ERROR") && v.contains("오류 상태"))
                .anyMatch(v -> v.contains("NOT_FOUND") && v.contains("겹친다"));
    }
}
