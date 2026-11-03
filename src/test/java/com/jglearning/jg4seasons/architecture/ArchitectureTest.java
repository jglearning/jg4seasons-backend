package com.jglearning.jg4seasons.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/** 운영 코드에 아키텍처 규칙을 적용한다. 규칙 자체가 위반을 잡는지는 ArchitectureRulesFixtureTest가 확인한다. */
@AnalyzeClasses(packages = ArchitectureTest.BASE, importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    static final String BASE = "com.jglearning.jg4seasons";

    @ArchTest
    static final ArchRule 컨트롤러는_리포지토리를_직접_호출하지_않는다 = ArchitectureRules.controllersDoNotUseRepositories();

    @ArchTest
    static final ArchRule 컨트롤러는_엔티티를_반환하지_않는다 = ArchitectureRules.controllersDoNotReturnEntities();

    @ArchTest
    static final ArchRule 시간은_주입된_Clock으로만_얻는다 = ArchitectureRules.noArgNowIsForbidden();

    @ArchTest
    static final ArchRule 학원_범위_리포지토리_메서드는_academyId를_받는다 =
            ArchitectureRules.academyScopedRepositoryMethodsTakeAcademyId();

    @ArchTest
    static final ArchRule 모듈은_다른_모듈의_api_패키지만_참조한다 = ArchitectureRules.modulesUseOnlyOtherModulesApi(BASE);

    @ArchTest
    static final ArchRule content는_listening에_의존하지_않는다 = ArchitectureRules.contentDoesNotDependOnListening(BASE);
}
