package com.jglearning.jg4seasons.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.jglearning.jg4seasons.infra.persistence.AcademyScoped;
import com.jglearning.jg4seasons.infra.persistence.AcademyScopedRepository;
import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaCodeUnitAccess;
import com.tngtech.archunit.core.domain.JavaGenericArrayType;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaParameterizedType;
import com.tngtech.archunit.core.domain.JavaType;
import com.tngtech.archunit.core.domain.JavaWildcardType;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityManager;
import java.lang.reflect.Parameter;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.MonthDay;
import java.time.OffsetDateTime;
import java.time.Year;
import java.time.YearMonth;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.QueryByExampleExecutor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

/** CLAUDE.md §9.1의 아키텍처 규칙. 실제 코드(ArchitectureTest)와 위반 예제(ArchitectureRulesFixtureTest)에 함께 쓴다. */
final class ArchitectureRules {

    static final List<String> MODULES =
            List.of("academy", "auth", "clazz", "content", "listening", "cloze", "words", "teacher", "admin", "infra");

    private static final String SHARED_MODULE = "infra";
    private static final String ACADEMY_ID = "academyId";
    private static final Set<String> SAVE_METHODS = Set.of("save", "saveAll", "saveAndFlush");
    private static final Set<String> TIME_TYPES = Set.of(
            Instant.class.getName(),
            LocalDate.class.getName(),
            LocalDateTime.class.getName(),
            LocalTime.class.getName(),
            ZonedDateTime.class.getName(),
            OffsetDateTime.class.getName(),
            Year.class.getName(),
            YearMonth.class.getName(),
            MonthDay.class.getName());
    private static final Set<String> SYSTEM_CLOCK_FACTORIES = Set.of("systemUTC", "systemDefaultZone", "system");

    /** 학원 조건 없는 메서드(findById·findAll 등)를 물려주는 Spring Data 인터페이스. */
    private static final List<Class<?>> UNSCOPED_REPOSITORY_TYPES = List.of(
            CrudRepository.class,
            ListCrudRepository.class,
            PagingAndSortingRepository.class,
            QueryByExampleExecutor.class,
            JpaSpecificationExecutor.class);

    /** 리포지토리 규칙을 거치지 않고 쿼리할 수 있는 타입. */
    private static final Class<?>[] DIRECT_DATA_ACCESS_TYPES = {
        EntityManager.class, JdbcTemplate.class, NamedParameterJdbcTemplate.class, JdbcClient.class
    };

    private static final Pattern ACADEMY_ID_PARAMETER_IN_QUERY = Pattern.compile(":academyId\\b");
    private static final Pattern DERIVED_CRITERIA_STARTS_WITH_ACADEMY_ID = Pattern.compile("^AcademyId(?![a-z]).*");
    private static final Pattern DERIVED_OR = Pattern.compile("Or(?=[A-Z])");

    private ArchitectureRules() {}

    static ArchRule controllersDoNotUseRepositories() {
        return noClasses()
                .that()
                .areMetaAnnotatedWith(Controller.class)
                .should()
                .dependOnClassesThat()
                .areAssignableTo(Repository.class)
                .because("컨트롤러는 서비스를 거친다")
                .allowEmptyShould(true);
    }

    /** 핸들러 메서드(@RequestMapping 계열, 접근 제한자 무관)의 반환 타입에 엔티티가 없어야 한다. */
    static ArchRule controllersDoNotReturnEntities() {
        return methods()
                .that()
                .areDeclaredInClassesThat()
                .areMetaAnnotatedWith(Controller.class)
                .and()
                .areMetaAnnotatedWith(RequestMapping.class)
                .should(new ArchCondition<JavaMethod>("엔티티를 반환하지 않는다(제네릭 인자·배열 포함)") {
                    @Override
                    public void check(JavaMethod method, ConditionEvents events) {
                        findEntity(method.getReturnType())
                                .ifPresent(entity -> events.add(SimpleConditionEvent.violated(
                                        method, method.getFullName() + "이(가) 엔티티 " + entity + "을(를) 반환한다")));
                    }
                })
                .allowEmptyShould(true);
    }

    /**
     * 시간은 주입된 Clock에서만. now는 now(Clock)만 허용(now(), now(ZoneId), 메서드 참조 모두 금지). 시스템 Clock은 {base}.infra.time에서만
     * 만든다.
     */
    static ArchRule timeComesOnlyFromInjectedClock(String basePackage) {
        String clockPackage = basePackage + ".infra.time";
        return classes()
                .should(new ArchCondition<JavaClass>("시간은 주입된 Clock으로만 얻는다") {
                    @Override
                    public void check(JavaClass javaClass, ConditionEvents events) {
                        boolean inClockPackage = javaClass.getPackageName().equals(clockPackage)
                                || javaClass.getPackageName().startsWith(clockPackage + ".");
                        Stream<JavaCodeUnitAccess<?>> accesses = Stream.concat(
                                javaClass.getMethodCallsFromSelf().stream(),
                                javaClass.getMethodReferencesFromSelf().stream());
                        accesses.forEach(access -> {
                            String owner = access.getTargetOwner().getName();
                            String name = access.getName();
                            List<JavaClass> parameters = access.getTarget().getRawParameterTypes();
                            boolean nowWithoutClock = TIME_TYPES.contains(owner)
                                    && name.equals("now")
                                    && !(parameters.size() == 1
                                            && parameters.get(0).isEquivalentTo(Clock.class));
                            boolean systemClock = owner.equals(Clock.class.getName())
                                    && SYSTEM_CLOCK_FACTORIES.contains(name)
                                    && !inClockPackage;
                            if (nowWithoutClock || systemClock) {
                                events.add(SimpleConditionEvent.violated(access, access.getDescription()));
                            }
                        });
                    }
                })
                .allowEmptyShould(true);
    }

    /**
     * 학원 범위 리포지토리: 조건 없는 메서드를 물려주는 인터페이스를 함께 상속하지 않는다. save 계열을 뺀 모든 메서드(상속 포함)는 academyId가 첫
     * 파라미터다. 파생 쿼리는 순서로 값을 묶으므로 이름도 By 바로 뒤가 AcademyId여야 하고 Or 조건이 없어야 한다. @Query는 :academyId를 쓴다.
     */
    static ArchRule academyScopedRepositoryMethodsTakeAcademyId() {
        return classes()
                .that()
                .areInterfaces()
                .and()
                .areAssignableTo(AcademyScopedRepository.class)
                .and()
                .doNotHaveFullyQualifiedName(AcademyScopedRepository.class.getName())
                .should(new ArchCondition<JavaClass>("학원 조건 없는 메서드를 갖지 않는다") {
                    @Override
                    public void check(JavaClass repository, ConditionEvents events) {
                        for (Class<?> unscoped : UNSCOPED_REPOSITORY_TYPES) {
                            if (repository.isAssignableTo(unscoped)) {
                                events.add(SimpleConditionEvent.violated(
                                        repository,
                                        repository.getName() + "이(가) " + unscoped.getSimpleName()
                                                + "을(를) 상속한다(학원 조건 없는 메서드가 생김)"));
                            }
                        }
                        for (JavaMethod method : repository.getAllMethods()) {
                            if (method.getOwner().isEquivalentTo(Object.class)
                                    || SAVE_METHODS.contains(method.getName())) {
                                continue;
                            }
                            academyIdProblems(method)
                                    .forEach(problem -> events.add(SimpleConditionEvent.violated(
                                            method, method.getFullName() + ": " + problem)));
                        }
                    }
                })
                .allowEmptyShould(true);
    }

    /** AcademyScoped 엔티티를 다루는 리포지토리는 AcademyScopedRepository를 상속해야 한다. */
    static ArchRule academyScopedEntitiesUseAcademyScopedRepository() {
        return classes()
                .that()
                .areInterfaces()
                .and()
                .areAssignableTo(Repository.class)
                .should(new ArchCondition<JavaClass>("학원 범위 엔티티는 AcademyScopedRepository로만 다룬다") {
                    @Override
                    public void check(JavaClass repository, ConditionEvents events) {
                        if (repository.isAssignableTo(AcademyScopedRepository.class)) {
                            return;
                        }
                        for (JavaType type : repository.getInterfaces()) {
                            if (type instanceof JavaParameterizedType parameterized
                                    && parameterized.toErasure().isAssignableTo(Repository.class)
                                    && !parameterized.getActualTypeArguments().isEmpty()) {
                                JavaClass domain = parameterized
                                        .getActualTypeArguments()
                                        .get(0)
                                        .toErasure();
                                if (domain.isAssignableTo(AcademyScoped.class)) {
                                    events.add(SimpleConditionEvent.violated(
                                            repository,
                                            repository.getName() + "이(가) 학원 범위 엔티티 " + domain.getName()
                                                    + "을(를) AcademyScopedRepository 없이 다룬다"));
                                }
                            }
                        }
                    }
                })
                .allowEmptyShould(true);
    }

    /** 다른 모듈은 공개 api 패키지로만 참조한다. infra는 공용이고 다른 모듈을 참조하지 않는다. 모듈 목록에 없는 패키지는 금지. */
    static ArchRule modulesUseOnlyOtherModulesApi(String basePackage) {
        return classes()
                .that()
                .resideInAPackage(basePackage + "..")
                .should(new ArchCondition<JavaClass>("다른 모듈은 공개 api 패키지로만 참조한다(infra는 공용)") {
                    @Override
                    public void check(JavaClass origin, ConditionEvents events) {
                        String originModule = moduleOf(origin.getPackageName(), basePackage);
                        if (originModule == null) {
                            if (!origin.getPackageName().equals(basePackage)) {
                                events.add(SimpleConditionEvent.violated(
                                        origin,
                                        origin.getName() + "이(가) 모듈 목록에 없는 패키지에 있다. MODULES에 추가하거나 기존 모듈로 옮긴다"));
                            }
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

    /**
     * EntityManager·JDBC 직접 사용은 `..persistence..` 패키지에서만. 서비스·컨트롤러가 리포지토리 규칙(학원 조건)을 우회해 쿼리하지
     * 못하게 한다. 모듈의 커스텀 리포지토리 구현은 그 모듈의 persistence 패키지에 둔다.
     */
    static ArchRule dataAccessOnlyInPersistencePackages() {
        return noClasses()
                .that()
                .resideOutsideOfPackage("..persistence..")
                .should()
                .dependOnClassesThat()
                .belongToAnyOf(DIRECT_DATA_ACCESS_TYPES)
                .because("데이터 직접 접근은 persistence 패키지에서만 한다(학원 조건 우회 방지)")
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

    private static List<String> academyIdProblems(JavaMethod method) {
        List<String> problems = new ArrayList<>();
        Parameter[] parameters = method.reflect().getParameters();
        if (Arrays.stream(parameters).anyMatch(p -> !p.isNamePresent())) {
            problems.add("파라미터 이름이 클래스 파일에 없다(-parameters 확인)");
            return problems;
        }
        if (parameters.length == 0 || !parameters[0].getName().equals(ACADEMY_ID)) {
            problems.add("academyId가 첫 파라미터가 아니다");
        }
        if (method.isAnnotatedWith(Query.class)) {
            String query = method.reflect().getAnnotation(Query.class).value();
            if (!ACADEMY_ID_PARAMETER_IN_QUERY.matcher(query).find()) {
                problems.add("@Query가 :academyId를 쓰지 않는다");
            }
            return problems;
        }
        String name = method.getName();
        int by = name.indexOf("By");
        if (by < 0) {
            problems.add("파생 쿼리 이름에 By가 없다");
            return problems;
        }
        String criteria = name.substring(by + 2);
        int orderBy = criteria.indexOf("OrderBy");
        if (orderBy >= 0) {
            criteria = criteria.substring(0, orderBy);
        }
        if (!DERIVED_CRITERIA_STARTS_WITH_ACADEMY_ID.matcher(criteria).matches()) {
            problems.add("파생 쿼리 조건이 AcademyId로 시작하지 않는다(값은 순서로 묶인다)");
        }
        if (DERIVED_OR.matcher(criteria).find()) {
            problems.add("파생 쿼리에 Or 조건이 있다(학원 조건을 벗어난다)");
        }
        return problems;
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
