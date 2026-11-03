package com.jglearning.jg4seasons.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.jglearning.archfixture.clazz.ScopedRepository;
import com.jglearning.archfixture.clazz.UnscopedRepository;
import com.jglearning.archfixture.cloze.ClozeGrader;
import com.jglearning.archfixture.cloze.ClozeHintReader;
import com.jglearning.archfixture.content.api.LessonView;
import com.jglearning.archfixture.listening.ClockCaller;
import com.jglearning.archfixture.listening.FixtureRepository;
import com.jglearning.archfixture.listening.NowCaller;
import com.jglearning.archfixture.listening.api.PlayCountQuery;
import com.jglearning.archfixture.listening.session.SessionStore;
import com.jglearning.archfixture.listening.web.DtoController;
import com.jglearning.archfixture.listening.web.EntityListController;
import com.jglearning.archfixture.listening.web.RepoCallingController;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

/** 규칙이 빈 대상에서 공짜로 통과하지 않는지: 위반 예제는 잡고 정상 예제는 통과시킨다. */
class ArchitectureRulesFixtureTest {

    private static final String FIXTURE_BASE = "com.jglearning.archfixture";

    private static boolean violates(ArchRule rule, Class<?>... classes) {
        JavaClasses imported = new ClassFileImporter().importClasses(classes);
        return rule.evaluate(imported).hasViolation();
    }

    @Test
    void 리포지토리를_부르는_컨트롤러를_잡는다() {
        assertThat(violates(
                        ArchitectureRules.controllersDoNotUseRepositories(),
                        RepoCallingController.class,
                        FixtureRepository.class))
                .isTrue();
    }

    @Test
    void 엔티티를_감싼_응답도_잡는다() {
        assertThat(violates(ArchitectureRules.controllersDoNotReturnEntities(), EntityListController.class))
                .isTrue();
    }

    @Test
    void DTO_응답은_통과한다() {
        assertThat(violates(ArchitectureRules.controllersDoNotReturnEntities(), DtoController.class))
                .isFalse();
    }

    @Test
    void 인자_없는_now를_잡는다() {
        assertThat(violates(ArchitectureRules.noArgNowIsForbidden(), NowCaller.class))
                .isTrue();
    }

    @Test
    void Clock을_받는_now는_통과한다() {
        assertThat(violates(ArchitectureRules.noArgNowIsForbidden(), ClockCaller.class))
                .isFalse();
    }

    @Test
    void academyId_없는_조회를_잡는다() {
        assertThat(violates(ArchitectureRules.academyScopedRepositoryMethodsTakeAcademyId(), UnscopedRepository.class))
                .isTrue();
    }

    @Test
    void academyId_있는_조회와_save는_통과한다() {
        assertThat(violates(ArchitectureRules.academyScopedRepositoryMethodsTakeAcademyId(), ScopedRepository.class))
                .isFalse();
    }

    @Test
    void 다른_모듈_내부를_부르면_잡는다() {
        assertThat(violates(
                        ArchitectureRules.modulesUseOnlyOtherModulesApi(FIXTURE_BASE),
                        ClozeGrader.class,
                        SessionStore.class))
                .isTrue();
    }

    @Test
    void 다른_모듈_api는_통과한다() {
        assertThat(violates(
                        ArchitectureRules.modulesUseOnlyOtherModulesApi(FIXTURE_BASE),
                        ClozeHintReader.class,
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
}
