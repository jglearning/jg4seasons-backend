package com.jglearning.archfixture.listening.web;

import com.jglearning.archfixture.listening.FixtureEntity;
import com.jglearning.archfixture.listening.FixtureRepository;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

/** 위반: @Controller + @ResponseBody 컨트롤러가 리포지토리를 부르고 엔티티를 반환한다. */
@Controller
@ResponseBody
public class ResponseBodyController {
    private final FixtureRepository repository;

    public ResponseBodyController(FixtureRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/fixture/count")
    public long count() {
        return repository.count();
    }

    @GetMapping("/fixture/one")
    public FixtureEntity one() {
        return new FixtureEntity();
    }
}
