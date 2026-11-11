package com.jglearning.jg4seasons.academy;

import com.jglearning.jg4seasons.academy.api.AcademyDirectory;
import com.jglearning.jg4seasons.academy.api.AcademyRegistry;
import com.jglearning.jg4seasons.academy.api.AcademySummary;
import com.jglearning.jg4seasons.academy.api.NewAcademy;
import com.jglearning.jg4seasons.infra.web.error.ApiException;
import com.jglearning.jg4seasons.infra.web.error.CommonErrorCode;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
class AcademyRegistryService implements AcademyRegistry {

    /** V1 academy의 UNIQUE(slug) 이름 — 동시에 같은 slug로 만든 경우만 409로 바꾼다 */
    private static final String SLUG_UNIQUE = "uk_academy_slug";

    private final AcademyRepository academies;
    private final AcademyDirectory directory;

    AcademyRegistryService(AcademyRepository academies, AcademyDirectory directory) {
        this.academies = academies;
        this.directory = directory;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public AcademySummary create(NewAcademy academy) {
        if (AcademyRegistry.RESERVED_SLUG.equals(academy.slug())) {
            throw ApiException.validation("slug", "demo는 쓸 수 없는 학원 주소예요");
        }
        if (academies.findBySlug(academy.slug()).isPresent()) {
            throw slugTaken(null);
        }
        Academy saved;
        try {
            saved = academies.saveAndFlush(
                    Academy.register(academy.name(), academy.slug(), academy.timezone(), academy.licenseEnforced()));
        } catch (DataIntegrityViolationException e) {
            if (!String.valueOf(e.getMostSpecificCause().getMessage()).contains(SLUG_UNIQUE)) {
                throw e;
            }
            throw slugTaken(e);
        }
        return toSummary(saved);
    }

    @Override
    public List<AcademySummary> list() {
        return academies.findAllByOrderByIdAsc().stream().map(this::toSummary).toList();
    }

    @Override
    public AcademySummary find(long academyId) {
        return academies
                .findById(academyId)
                .map(this::toSummary)
                .orElseThrow(() -> new ApiException(CommonErrorCode.NOT_FOUND, "학원을 찾을 수 없어요"));
    }

    private AcademySummary toSummary(Academy academy) {
        return new AcademySummary(
                academy.getId(),
                academy.getName(),
                academy.getSlug(),
                academy.getStatus(),
                directory.zoneOf(academy.getId()),
                academy.isLicenseEnforced());
    }

    private static ApiException slugTaken(Throwable cause) {
        return cause == null
                ? new ApiException(AcademyErrorCode.ACADEMY_SLUG_TAKEN, "다른 학원 주소를 써 주세요")
                : new ApiException(AcademyErrorCode.ACADEMY_SLUG_TAKEN, "다른 학원 주소를 써 주세요", cause);
    }
}
