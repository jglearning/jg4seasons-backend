package com.jglearning.archfixture.listening;

import jakarta.persistence.EntityManager;

/** 위반: 서비스가 EntityManager로 직접 조회한다. */
public class RawEntityManagerService {
    private final EntityManager entityManager;

    public RawEntityManagerService(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public FixtureEntity find(Long id) {
        return entityManager.find(FixtureEntity.class, id);
    }
}
