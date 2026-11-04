package com.jglearning.jg4seasons.infra.security;

import jakarta.annotation.security.DenyAll;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * 서버가 뜰 때 컨트롤러 접근 규칙을 확인하고, 어긋나면 기동을 멈춘다(ArchitectureTest.모든_컨트롤러_메서드에_접근_규칙이_있다와 같은 규칙).
 *
 * <ul>
 *   <li>이 앱 패키지의 모든 핸들러 메서드는 메서드나 클래스에 @PermitAll·@RolesAllowed·@DenyAll 중 하나가 있다
 *   <li>/api/ 경로의 @PermitAll 메서드 = PublicEndpoints.NO_AUTH(URL 층에서 여는 공개 경로)와 정확히 같다. 한쪽에만 있으면 공개 API가
 *       URL 층에서 막히거나(401), 메서드에 공개 표시만 있고 실제로는 막혀 있어 읽는 사람을 속인다
 * </ul>
 */
@Component
class AccessRuleVerifier implements SmartInitializingSingleton {

    static final String BASE_PACKAGE = "com.jglearning.jg4seasons";
    private static final String API_PREFIX = "/api/";
    private static final List<Class<? extends Annotation>> RULES =
            List.of(PermitAll.class, RolesAllowed.class, DenyAll.class);

    private final RequestMappingHandlerMapping handlerMapping;

    AccessRuleVerifier(RequestMappingHandlerMapping requestMappingHandlerMapping) {
        this.handlerMapping = requestMappingHandlerMapping;
    }

    @Override
    public void afterSingletonsInstantiated() {
        List<String> violations = violations(handlerMapping.getHandlerMethods(), BASE_PACKAGE, PublicEndpoints.NO_AUTH);
        if (!violations.isEmpty()) {
            throw new IllegalStateException("컨트롤러 접근 규칙이 맞지 않는다:\n  " + String.join("\n  ", violations));
        }
    }

    static List<String> violations(
            Map<RequestMappingInfo, HandlerMethod> handlers, String basePackage, Collection<String> publicPaths) {
        List<String> violations = new ArrayList<>();
        Set<String> permitAllApiPaths = new TreeSet<>();
        Set<String> handledPaths = new TreeSet<>();
        handlers.forEach((info, handler) -> {
            Class<?> type = handler.getBeanType();
            if (!type.getName().startsWith(basePackage + ".")) {
                return;
            }
            String name = type.getSimpleName() + "#" + handler.getMethod().getName();
            Set<String> paths = info.getPatternValues();
            handledPaths.addAll(paths);
            if (!hasRule(handler)) {
                violations.add("접근 규칙 없음: " + name + " " + paths);
                return;
            }
            boolean permitAll = AnnotatedElementUtils.hasAnnotation(handler.getMethod(), PermitAll.class)
                    || (!hasMethodRule(handler) && AnnotatedElementUtils.hasAnnotation(type, PermitAll.class));
            for (String path : paths) {
                if (!path.startsWith(API_PREFIX)) {
                    continue;
                }
                if (permitAll) {
                    permitAllApiPaths.add(path);
                    if (!publicPaths.contains(path)) {
                        violations.add("공개 목록에 없는 @PermitAll: " + name + " " + path);
                    }
                } else if (publicPaths.contains(path)) {
                    violations.add("@PermitAll이 아닌 공개 경로: " + name + " " + path);
                }
            }
        });
        for (String path : publicPaths) {
            if (!handledPaths.contains(path)) {
                violations.add("핸들러 없는 공개 경로: " + path);
            }
        }
        return violations;
    }

    private static boolean hasRule(HandlerMethod handler) {
        return hasMethodRule(handler)
                || RULES.stream().anyMatch(rule -> AnnotatedElementUtils.hasAnnotation(handler.getBeanType(), rule));
    }

    private static boolean hasMethodRule(HandlerMethod handler) {
        return RULES.stream().anyMatch(rule -> AnnotatedElementUtils.hasAnnotation(handler.getMethod(), rule));
    }
}
