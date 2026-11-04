package com.jglearning.jg4seasons.academy;

import com.jglearning.jg4seasons.academy.api.AcademyDirectory;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
class AcademyDirectoryService implements AcademyDirectory {

    private final AcademyRepository academies;

    AcademyDirectoryService(AcademyRepository academies) {
        this.academies = academies;
    }

    @Override
    public String nameOf(long academyId) {
        return academies
                .findById(academyId)
                .map(Academy::getName)
                .orElseThrow(() -> new IllegalStateException("학원이 없다: " + academyId));
    }

    @Override
    public boolean isActive(long academyId) {
        return academies.findById(academyId).map(Academy::isActive).orElse(false);
    }

    @Override
    public Optional<Long> findIdBySlug(String slug) {
        return academies.findBySlug(slug).map(Academy::getId);
    }
}
