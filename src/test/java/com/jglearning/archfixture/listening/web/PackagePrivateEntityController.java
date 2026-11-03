package com.jglearning.archfixture.listening.web;

import com.jglearning.archfixture.listening.FixtureEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** 위반: public이 아니어도 Spring MVC는 핸들러로 호출한다. */
@RestController
public class PackagePrivateEntityController {
    @GetMapping("/fixture/entity")
    FixtureEntity get() {
        return new FixtureEntity();
    }
}
