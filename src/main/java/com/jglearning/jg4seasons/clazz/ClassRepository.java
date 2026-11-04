package com.jglearning.jg4seasons.clazz;

import com.jglearning.jg4seasons.infra.persistence.AcademyScopedRepository;
import java.util.Optional;

interface ClassRepository extends AcademyScopedRepository<Clazz, Long> {

    Optional<Clazz> findByAcademyIdAndId(Long academyId, Long id);
}
