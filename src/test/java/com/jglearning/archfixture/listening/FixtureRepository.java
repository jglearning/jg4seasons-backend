package com.jglearning.archfixture.listening;

import org.springframework.data.repository.Repository;

public interface FixtureRepository extends Repository<FixtureEntity, Long> {
    long count();
}
