package com.jglearning.archfixture.listening.web;

import com.jglearning.archfixture.listening.FixtureDto;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** 정상: DTO를 반환한다. */
@RestController
public class DtoController {
    @GetMapping("/fixture/dto")
    public FixtureDto get() {
        return new FixtureDto(1L);
    }
}
