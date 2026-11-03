package com.jglearning.jg4seasons.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.jglearning.jg4seasons.infra.persistence.AcademyScopedRepository;
import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaGenericArrayType;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.domain.JavaParameterizedType;
import com.tngtech.archunit.core.domain.JavaType;
import com.tngtech.archunit.core.domain.JavaWildcardType;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import jakarta.persistence.Entity;
import java.lang.reflect.Parameter;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.repository.Repository;
import org.springframework.web.bind.annotation.RestController;

/** CLAUDE.md §9.1의 아키텍처 규칙. 실제 코드(ArchitectureTest)와 위반 예제(ArchitectureRulesFixtureTest)에 함께 쓴다. */
final class ArchitectureRules {

    static final List<String> MODULES =
            List.of("academy", "auth", "clazz", "content", "listening", "cloze", "words", "teacher", "admin", "infra");

    private static final String SHARED_MODULE = "infra";
    private static final Set<String> SAVE_METHODS = Set.of("save", "saveAll", "saveAndFlush");
    private static final Set<String> TIME_TYPES = Set.of(
            Instant.class.getName(),
            LocalDate.class.getName(),
            LocalDateTime.class.getName(),
            LocalTime.class.getName(),
            ZonedDateTime.class.getName(),
            OffsetDateTime.class.getName());

    private ArchitectureRules() {}

    static ArchRule controllersDoNotUseRepositories() {
        return noClasses()
                .that()
                .areAnnotatedWith(RestController.class)
                .should()
                .dependOnClassesThat()
                .areAssignableTo(Repository.class)
                .because("컨트롤러는 서비스를 거친다")
                .allowEmptyShould(true);
    }

    static ArchRule controllersDoNotReturnEntities() {
        return methods()
                .that()
                .areDeclaredInClassesThat()
                .areAnnotatedWith(RestController.class)
                .and()
                .arePublic()
                .should(new ArchCondition<JavaMethod>("엔티티를 반환하지 않는다(제네릭 타입 인자 포함)") {
                    @Override
                    public void check(JavaMethod method, ConditionEvents events) {
                        findEntity(method.getReturnType())
                                .ifPresent(entity -> events.add(SimpleConditionEvent.violated(
                                        method, method.getFullName() + "이(가) 엔티티 " + entity + "을(를) 반환한다")));
                    }
                })
                .allowEmptyShould(true);
    }

    static ArchRule noArgNowIsForbidden() {
        return classes()
                .should(new ArchCondition<JavaClass>("인자 없는 now()를 부르지 않는다(주입된 Clock만)") {
                    @Override
                    public void check(JavaClass javaClass, ConditionEvents events) {
                        for (JavaMethodCall call : javaClass.getMethodCallsFromSelf()) {
                            boolean noArgNow = call.getName().equals("now")
                                    && call.getTarget().getRawParameterTypes().isEmpty()
                                    && TIME_TYPES.contains(call.getTargetOwner().getName());
                            if (noArgNow) {
                                events.add(SimpleConditionEvent.violated(call, call.getDescription()));
                            }
                        }
                    }
                })
                .allowEmptyShould(true);
    }

    static ArchRule academyScopedRepositoryMethodsTakeAcademyId() {
        return classes()
                .that()
                .areInterfaces()
                .and()
                .areAssignableTo(AcademyScopedRepository.class)
                .and()
                .doNotHaveFullyQualifiedName(AcademyScopedRepository.class.getName())
                .should(new ArchCondition<JavaClass>("save 계열을 뺀 선언 메서드는 academyId 파라미터를 받는다") {
                    @Override
                    public void check(JavaClass repository, ConditionEvents events) {
                        for (JavaMethod method : repository.getMethods()) {
                            if (SAVE_METHODS.contains(method.getName())) {
                                continue;
                            }
                            Parameter[] parameters = method.reflect().getParameters();
                            if (Arrays.stream(parameters).anyMatch(p -> !p.isNamePresent())) {
                                events.add(SimpleConditionEvent.violated(
                                        method, method.getFullName() + ": 파라미터 이름이 클래스 파일에 없다(-parameters 확인)"));
                            } else if (Arrays.stream(parameters)
                                    .noneMatch(p -> p.getName().equals("academyId"))) {
                                events.add(SimpleConditionEvent.violated(
                                        method, method.getFullName() + "에 academyId 파라미터가 없다"));
                            }
                        }
                    }
                })
                .allowEmptyShould(true);
    }

    static ArchRule modulesUseOnlyOtherModulesApi(String basePackage) {
        return classes()
                .that()
                .resideInAPackage(basePackage + "..")
                .should(new ArchCondition<JavaClass>("다른 모듈은 공개 api 패키지로만 참조한다(infra는 공용)") {
                    @Override
                    public void check(JavaClass origin, ConditionEvents events) {
                        String originModule = moduleOf(origin.getPackageName(), basePackage);
                        if (originModule == null) {
                            return;
                        }
                        for (Dependency dependency : origin.getDirectDependenciesFromSelf()) {
                            String targetPackage = dependency.getTargetClass().getPackageName();
                            String targetModule = moduleOf(targetPackage, basePackage);
                            if (targetModule == null
                                    || targetModule.equals(originModule)
                                    || targetModule.equals(SHARED_MODULE)) {
                                continue;
                            }
                            String api = basePackage + "." + targetModule + ".api";
                            boolean viaApi = !originModule.equals(SHARED_MODULE)
                                    && (targetPackage.equals(api) || targetPackage.startsWith(api + "."));
                            if (!viaApi) {
                                events.add(SimpleConditionEvent.violated(dependency, dependency.getDescription()));
                            }
                        }
                    }
                })
                .allowEmptyShould(true);
    }

    static ArchRule contentDoesNotDependOnListening(String basePackage) {
        return noClasses()
                .that()
                .resideInAPackage(basePackage + ".content..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage(basePackage + ".listening..")
                .because("listening이 content에 의존한다. 역방향은 순환이 된다")
                .allowEmptyShould(true);
    }

    /** 패키지가 속한 모듈 이름. 모듈 패키지가 아니면 null. */
    static String moduleOf(String packageName, String basePackage) {
        if (!packageName.startsWith(basePackage + ".")) {
            return null;
        }
        String rest = packageName.substring(basePackage.length() + 1);
        int dot = rest.indexOf('.');
        String first = dot < 0 ? rest : rest.substring(0, dot);
        return MODULES.contains(first) ? first : null;
    }

    private static Optional<String> findEntity(JavaType type) {
        if (type instanceof JavaParameterizedType parameterized) {
            for (JavaType argument : parameterized.getActualTypeArguments()) {
                Optional<String> found = findEntity(argument);
                if (found.isPresent()) {
                    return found;
                }
            }
        } else if (type instanceof JavaWildcardType wildcard) {
            for (JavaType bound : wildcard.getUpperBounds()) {
                Optional<String> found = findEntity(bound);
                if (found.isPresent()) {
                    return found;
                }
            }
        } else if (type instanceof JavaGenericArrayType array) {
            return findEntity(array.getComponentType());
        }
        JavaClass erasure = type.toErasure().getBaseComponentType();
        return erasure.isAnnotatedWith(Entity.class) ? Optional.of(erasure.getName()) : Optional.empty();
    }
}
