package com.jglearning.archfixture.listening.web;

import jakarta.annotation.security.DenyAll;
import jakarta.annotation.security.RolesAllowed;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** 정상: 클래스에 붙은 규칙도 인정한다. 메서드 규칙이 있으면 그것이 우선. */
@RestController
@RolesAllowed("TEACHER")
public class ClassLevelRuleController {
    @GetMapping("/fixture/class-rule")
    public String byClass() {
        return "x";
    }

    @DenyAll
    @GetMapping("/fixture/denied")
    public String denied() {
        return "x";
    }
}
