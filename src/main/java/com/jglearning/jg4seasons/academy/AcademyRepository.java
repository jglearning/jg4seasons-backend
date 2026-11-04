package com.jglearning.jg4seasons.academy;

import java.util.Optional;
import org.springframework.data.repository.Repository;

interface AcademyRepository extends Repository<Academy, Long> {

    Optional<Academy> findById(Long id);

    Optional<Academy> findBySlug(String slug);
}
