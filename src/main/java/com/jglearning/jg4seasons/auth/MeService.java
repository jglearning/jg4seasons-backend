package com.jglearning.jg4seasons.auth;

import com.jglearning.jg4seasons.academy.api.AcademyDirectory;
import com.jglearning.jg4seasons.auth.web.dto.MeResponse;
import com.jglearning.jg4seasons.infra.web.error.ApiException;
import com.jglearning.jg4seasons.infra.web.error.CommonErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 1.7 내 정보. 사용자·학원은 access JWT(sub·aca)로만 정한다 — 요청 값으로 받지 않는다. */
@Service
@Transactional(readOnly = true)
public class MeService {

    private final UserRepository users;
    private final AcademyDirectory academies;

    MeService(UserRepository users, AcademyDirectory academies) {
        this.users = users;
        this.academies = academies;
    }

    /** 사용자가 그 학원에 없거나 비활성이거나 학원이 정지면 401 UNAUTHORIZED(다시 입장) */
    public MeResponse me(long userId, long academyId) {
        User user = users.findByAcademyIdAndId(academyId, userId)
                .filter(User::isActive)
                .filter(found -> academies.isActive(academyId))
                .orElseThrow(() -> new ApiException(CommonErrorCode.UNAUTHORIZED));
        return toResponse(user, academyId);
    }

    MeResponse toResponse(User user, long academyId) {
        return new MeResponse(
                user.getId(),
                user.getName(),
                user.getRole().value(),
                new MeResponse.AcademyRef(academyId, academies.nameOf(academyId)));
    }
}
