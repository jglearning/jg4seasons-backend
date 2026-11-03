package com.jglearning.archfixture.listening.web;

import com.jglearning.archfixture.listening.FixtureRepository;
import org.springframework.web.bind.annotation.RestController;

/** 위반: 컨트롤러가 리포지토리를 직접 호출한다. */
@RestController
public class RepoCallingController {
    private final FixtureRepository repository;

    public RepoCallingController(FixtureRepository repository) {
        this.repository = repository;
    }

    public long count() {
        return repository.count();
    }
}
