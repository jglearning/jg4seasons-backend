package com.jglearning.archfixture.listening.web;

import jakarta.annotation.security.RolesAllowed;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** 위반: 접근 규칙(@PermitAll·@RolesAllowed·@DenyAll)이 없는 핸들러 메서드가 하나 있다. */
@RestController
public class NoAccessRuleController {
    @GetMapping("/fixture/no-rule")
    public String noRule() {
        return "x";
    }

    @RolesAllowed("STUDENT")
    @GetMapping("/fixture/with-rule")
    public String withRule() {
        return "x";
    }
}
