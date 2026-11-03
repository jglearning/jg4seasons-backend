package com.jglearning.archfixture.clazz;

import com.jglearning.jg4seasons.infra.persistence.AcademyScoped;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/** 학원 범위 엔티티 예제. */
@Entity
public class ScopedFixtureEntity implements AcademyScoped {
    @Id
    private Long id;

    private Long academyId;

    @Override
    public Long getAcademyId() {
        return academyId;
    }
}
