package com.jglearning.jg4seasons.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.jglearning.archfixture.auth.RosterMaker;
import com.jglearning.archfixture.clazz.CrudLeakingRepository;
import com.jglearning.archfixture.clazz.OrLeakRepository;
import com.jglearning.archfixture.clazz.QueryIgnoringRepository;
import com.jglearning.archfixture.clazz.RawScopedRepository;
import com.jglearning.archfixture.clazz.ScopedRepository;
import com.jglearning.archfixture.clazz.UnscopedRepository;
import com.jglearning.archfixture.clazz.WrongOrderRepository;
import com.jglearning.archfixture.common.Helper;
import com.jglearning.archfixture.content.api.LessonView;
import com.jglearning.archfixture.infra.InfraUsingApi;
import com.jglearning.archfixture.infra.time.FixtureClockConfig;
import com.jglearning.archfixture.listening.ClockCaller;
import com.jglearning.archfixture.listening.FixtureRepository;
import com.jglearning.archfixture.listening.NowCaller;
import com.jglearning.archfixture.listening.NowReferenceCaller;
import com.jglearning.archfixture.listening.RawEntityManagerService;
import com.jglearning.archfixture.listening.RawJdbcService;
import com.jglearning.archfixture.listening.SystemClockCaller;
import com.jglearning.archfixture.listening.YearNowCaller;
import com.jglearning.archfixture.listening.ZoneNowCaller;
import com.jglearning.archfixture.listening.api.PlayCountQuery;
import com.jglearning.archfixture.listening.apix.Lookalike;
import com.jglearning.archfixture.listening.persistence.CustomQueryRepository;
import com.jglearning.archfixture.listening.session.SessionStore;
import com.jglearning.archfixture.listening.web.ClassLevelRuleController;
import com.jglearning.archfixture.listening.web.DtoController;
import com.jglearning.archfixture.listening.web.EntityListController;
import com.jglearning.archfixture.listening.web.NoAccessRuleController;
import com.jglearning.archfixture.listening.web.PackagePrivateEntityController;
import com.jglearning.archfixture.listening.web.RepoCallingController;
import com.jglearning.archfixture.listening.web.ResponseBodyController;
import com.jglearning.archfixture.teacher.ScheduleBypasser;
import com.jglearning.archfixture.teacher.ScopeBypasser;
import com.jglearning.archfixture.teacher.ScopeNarrower;
import com.jglearning.archfixture.teacher.TeacherScope;
import com.jglearning.archfixture.words.ApiPrefixUser;
import com.jglearning.archfixture.words.WordPlayCountReader;
import com.jglearning.archfixture.words.WordStudyRecorder;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 규칙이 빈 대상에서 공짜로 통과하지 않는지: 위반 예제는 잡고 정상 예제는 통과시킨다. */
class ArchitectureRulesFixtureTest {

    private static final String FIXTURE_BASE = "com.jglearning.archfixture";

    private static JavaClasses classes(Class<?>... classes) {
        return new ClassFileImporter().importClasses(classes);
    }

    private static boolean violates(ArchRule rule, Class<?>... classes) {
        return rule.evaluate(classes(classes)).hasViolation();
    }

    private static List<String> violationDetails(ArchRule rule, Class<?>... classes) {
        return rule.evaluate(classes(classes)).getFailureReport().getDetails();
    }

    private static int violationCount(ArchRule rule, Class<?>... classes) {
        return violationDetails(rule, classes).size();
    }

    private static ArchRule timeRule() {
        return ArchitectureRules.timeComesOnlyFromInjectedClock(FIXTURE_BASE);
    }

    // 컨트롤러

    @Test
    void 리포지토리를_부르는_컨트롤러를_잡는다() {
        assertThat(violates(
                        ArchitectureRules.controllersDoNotUseRepositories(),
                        RepoCallingController.class,
                        FixtureRepository.class))
                .isTrue();
    }

    @Test
    void ResponseBody_컨트롤러가_리포지토리를_불러도_잡는다() {
        assertThat(violates(
                        ArchitectureRules.controllersDoNotUseRepositories(),
                        ResponseBodyController.class,
                        FixtureRepository.class))
                .isTrue();
    }

    @Test
    void 엔티티를_감싼_응답_배열_Optional을_모두_잡는다() {
        assertThat(violationCount(ArchitectureRules.controllersDoNotReturnEntities(), EntityListController.class))
                .isEqualTo(3);
    }

    @Test
    void public이_아닌_핸들러의_엔티티_반환도_잡는다() {
        assertThat(violates(ArchitectureRules.controllersDoNotReturnEntities(), PackagePrivateEntityController.class))
                .isTrue();
    }

    @Test
    void ResponseBody_컨트롤러의_엔티티_반환도_잡는다() {
        assertThat(violates(ArchitectureRules.controllersDoNotReturnEntities(), ResponseBodyController.class))
                .isTrue();
    }

    @Test
    void DTO_응답은_통과한다() {
        assertThat(violates(ArchitectureRules.controllersDoNotReturnEntities(), DtoController.class))
                .isFalse();
    }

    // 시간

    @Test
    void 인자_없는_now를_잡는다() {
        assertThat(violates(timeRule(), NowCaller.class)).isTrue();
    }

    @Test
    void 타임존만_주는_now도_잡는다() {
        assertThat(violates(timeRule(), ZoneNowCaller.class)).isTrue();
    }

    @Test
    void now_메서드_참조도_잡는다() {
        assertThat(violates(timeRule(), NowReferenceCaller.class)).isTrue();
    }

    @Test
    void Year_now도_잡는다() {
        assertThat(violates(timeRule(), YearNowCaller.class)).isTrue();
    }

    @Test
    void infra_time_밖에서_시스템_Clock을_만들면_잡는다() {
        assertThat(violates(timeRule(), SystemClockCaller.class)).isTrue();
    }

    @Test
    void Clock을_받는_now와_infra_time의_시스템_Clock은_통과한다() {
        assertThat(violates(timeRule(), ClockCaller.class, FixtureClockConfig.class))
                .isFalse();
    }

    // 학원 범위 리포지토리

    @Test
    void academyId_없는_조회를_잡는다() {
        assertThat(violates(ArchitectureRules.academyScopedRepositoryMethodsTakeAcademyId(), UnscopedRepository.class))
                .isTrue();
    }

    @Test
    void CRUD_리포지토리를_함께_상속하면_잡는다() {
        // 물려받은 findById도 academyId 검사에 걸리지만, 상속 자체를 금지하는 검사가 따로 동작하는지 메시지로 확인한다
        assertThat(violationDetails(
                        ArchitectureRules.academyScopedRepositoryMethodsTakeAcademyId(), CrudLeakingRepository.class))
                .anyMatch(detail -> detail.contains("CrudRepository을(를) 상속한다"));
    }

    @Test
    void 파생_쿼리_조건이_AcademyId로_시작하지_않으면_잡는다() {
        assertThat(violates(
                        ArchitectureRules.academyScopedRepositoryMethodsTakeAcademyId(), WrongOrderRepository.class))
                .isTrue();
    }

    @Test
    void academyId_조건을_Or로_묶으면_잡는다() {
        assertThat(violates(ArchitectureRules.academyScopedRepositoryMethodsTakeAcademyId(), OrLeakRepository.class))
                .isTrue();
    }

    @Test
    void Query가_academyId를_쓰지_않으면_잡는다() {
        assertThat(violates(
                        ArchitectureRules.academyScopedRepositoryMethodsTakeAcademyId(), QueryIgnoringRepository.class))
                .isTrue();
    }

    @Test
    void academyId로_시작하는_조회와_Query와_save는_통과한다() {
        assertThat(violates(ArchitectureRules.academyScopedRepositoryMethodsTakeAcademyId(), ScopedRepository.class))
                .isFalse();
    }

    @Test
    void 학원_범위_엔티티를_일반_리포지토리로_다루면_잡는다() {
        assertThat(violates(
                        ArchitectureRules.academyScopedEntitiesUseAcademyScopedRepository(), RawScopedRepository.class))
                .isTrue();
    }

    @Test
    void 학원_범위_리포지토리와_일반_엔티티의_리포지토리는_통과한다() {
        assertThat(violates(
                        ArchitectureRules.academyScopedEntitiesUseAcademyScopedRepository(),
                        ScopedRepository.class,
                        FixtureRepository.class))
                .isFalse();
    }

    // 데이터 직접 접근

    @Test
    void 서비스가_JdbcTemplate을_쓰면_잡는다() {
        assertThat(violates(ArchitectureRules.dataAccessOnlyInPersistencePackages(), RawJdbcService.class))
                .isTrue();
    }

    @Test
    void 서비스가_EntityManager를_쓰면_잡는다() {
        assertThat(violates(ArchitectureRules.dataAccessOnlyInPersistencePackages(), RawEntityManagerService.class))
                .isTrue();
    }

    @Test
    void persistence_패키지의_JDBC는_통과한다() {
        assertThat(violates(ArchitectureRules.dataAccessOnlyInPersistencePackages(), CustomQueryRepository.class))
                .isFalse();
    }

    // 모듈

    @Test
    void 다른_모듈_내부를_부르면_잡는다() {
        assertThat(violates(
                        ArchitectureRules.modulesUseOnlyOtherModulesApi(FIXTURE_BASE),
                        WordStudyRecorder.class,
                        SessionStore.class))
                .isTrue();
    }

    @Test
    void 이름만_api로_시작하는_패키지는_api가_아니다() {
        assertThat(violates(
                        ArchitectureRules.modulesUseOnlyOtherModulesApi(FIXTURE_BASE),
                        ApiPrefixUser.class,
                        Lookalike.class))
                .isTrue();
    }

    @Test
    void infra가_다른_모듈_api를_불러도_잡는다() {
        assertThat(violates(
                        ArchitectureRules.modulesUseOnlyOtherModulesApi(FIXTURE_BASE),
                        InfraUsingApi.class,
                        PlayCountQuery.class))
                .isTrue();
    }

    @Test
    void 모듈_목록에_없는_패키지를_잡는다() {
        assertThat(violates(
                        ArchitectureRules.modulesUseOnlyOtherModulesApi(FIXTURE_BASE),
                        Helper.class,
                        SessionStore.class))
                .isTrue();
    }

    @Test
    void 다른_모듈_api는_통과한다() {
        assertThat(violates(
                        ArchitectureRules.modulesUseOnlyOtherModulesApi(FIXTURE_BASE),
                        WordPlayCountReader.class,
                        PlayCountQuery.class))
                .isFalse();
    }

    @Test
    void content가_listening을_부르면_잡는다() {
        assertThat(violates(
                        ArchitectureRules.contentDoesNotDependOnListening(FIXTURE_BASE),
                        LessonView.class,
                        PlayCountQuery.class))
                .isTrue();
    }

    @Test
    void 접근_규칙_없는_핸들러를_잡는다() {
        List<String> details =
                violationDetails(ArchitectureRules.controllerMethodsHaveAccessRule(), NoAccessRuleController.class);

        assertThat(details).hasSize(1);
        assertThat(details.get(0)).contains("noRule");
    }

    @Test
    void 클래스에_붙은_접근_규칙도_인정한다() {
        assertThat(violates(ArchitectureRules.controllerMethodsHaveAccessRule(), ClassLevelRuleController.class))
                .isFalse();
    }

    // 진도표 쓰기

    @Test
    void TeacherScope_밖에서_진도표_api를_부르면_잡는다() {
        assertThat(violates(
                        ArchitectureRules.classSchedulesOnlyThroughTeacherScope(FIXTURE_BASE), ScheduleBypasser.class))
                .isTrue();
    }

    @Test
    void TeacherScope는_진도표_api를_부를_수_있다() {
        assertThat(violates(ArchitectureRules.classSchedulesOnlyThroughTeacherScope(FIXTURE_BASE), TeacherScope.class))
                .isFalse();
    }

    // 학원 학생 묶음

    @Test
    void auth_밖에서_학원_학생_묶음을_만들면_호출과_메서드_참조_둘_다_잡는다() {
        List<String> details = violationDetails(
                ArchitectureRules.academyStudentsAreCreatedOnlyInAuth(FIXTURE_BASE), ScopeBypasser.class);

        assertThat(details).hasSize(2);
        assertThat(details).anyMatch(d -> d.contains("call("));
        assertThat(details).anyMatch(d -> d.contains("reference("));
    }

    @Test
    void auth_안에서_만들고_밖에서_좁히기만_하면_통과한다() {
        assertThat(violates(
                        ArchitectureRules.academyStudentsAreCreatedOnlyInAuth(FIXTURE_BASE),
                        RosterMaker.class,
                        ScopeNarrower.class))
                .isFalse();
    }
}
