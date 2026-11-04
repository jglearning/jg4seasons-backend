package com.jglearning.jg4seasons.infra.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.jglearning.archfixture.listening.web.DtoController;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;

/** 기동 때 검사: 모든 핸들러에 접근 규칙, /api의 @PermitAll ⇔ 공개 목록(PublicEndpoints). */
class AccessRuleVerifierTest {

    static final String BASE = "com.jglearning.jg4seasons";

    static class Handlers {
        @PermitAll
        public void login() {}

        @RolesAllowed("STUDENT")
        public void me() {}

        public void noRule() {}

        @PermitAll
        public void secretPublic() {}

        @PermitAll
        public void error() {}
    }

    @RolesAllowed("TEACHER")
    static class ClassRuled {
        public void list() {}
    }

    final Map<RequestMappingInfo, HandlerMethod> handlers = new LinkedHashMap<>();

    void add(String path, Object bean, String method) throws Exception {
        handlers.put(
                RequestMappingInfo.paths(path).methods(RequestMethod.POST).build(),
                new HandlerMethod(bean, bean.getClass().getMethod(method)));
    }

    @Test
    void 규칙이_있고_공개_경로가_목록과_같으면_통과() throws Exception {
        add("/api/v1/auth/login", new Handlers(), "login");
        add("/api/v1/me", new Handlers(), "me");
        add("/api/v1/teacher/classes", new ClassRuled(), "list");
        add("/error", new Handlers(), "error");

        assertThat(AccessRuleVerifier.violations(handlers, BASE, List.of("/api/v1/auth/login")))
                .isEmpty();
    }

    @Test
    void 규칙_없는_핸들러를_찾는다() throws Exception {
        add("/api/v1/x", new Handlers(), "noRule");

        assertThat(AccessRuleVerifier.violations(handlers, BASE, List.of()))
                .singleElement()
                .asString()
                .contains("접근 규칙 없음")
                .contains("noRule");
    }

    @Test
    void 목록에_없는_api_공개_메서드를_찾는다() throws Exception {
        add("/api/v1/admin/secret", new Handlers(), "secretPublic");

        assertThat(AccessRuleVerifier.violations(handlers, BASE, List.of()))
                .singleElement()
                .asString()
                .contains("공개 목록에 없는 @PermitAll")
                .contains("/api/v1/admin/secret");
    }

    @Test
    void 공개_목록인데_PermitAll이_아닌_경로를_찾는다() throws Exception {
        add("/api/v1/auth/login", new Handlers(), "me");

        assertThat(AccessRuleVerifier.violations(handlers, BASE, List.of("/api/v1/auth/login")))
                .anySatisfy(v -> assertThat(v).contains("@PermitAll이 아닌 공개 경로").contains("/api/v1/auth/login"));
    }

    @Test
    void 공개_목록에_있는데_핸들러가_없는_경로를_찾는다() {
        assertThat(AccessRuleVerifier.violations(handlers, BASE, List.of("/api/v1/auth/gone")))
                .singleElement()
                .asString()
                .contains("핸들러 없는 공개 경로")
                .contains("/api/v1/auth/gone");
    }

    @Test
    void 우리_패키지_밖_핸들러는_검사하지_않는다() throws Exception {
        add("/fixture/dto", new DtoController(), "get");

        assertThat(AccessRuleVerifier.violations(handlers, BASE, List.of())).isEmpty();
    }
}
