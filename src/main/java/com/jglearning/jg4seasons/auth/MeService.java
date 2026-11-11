package com.jglearning.jg4seasons.auth;

import com.jglearning.jg4seasons.academy.api.AcademyDirectory;
import com.jglearning.jg4seasons.auth.persistence.AuthUser;
import com.jglearning.jg4seasons.auth.persistence.PreAuthUserLookup;
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
    private final PreAuthUserLookup preAuthUsers;

    MeService(UserRepository users, AcademyDirectory academies, PreAuthUserLookup preAuthUsers) {
        this.users = users;
        this.academies = academies;
        this.preAuthUsers = preAuthUsers;
    }

    /**
     * academyId는 JWT aca(운영자면 null). 사용자가 그 학원에 없거나 비활성이거나 학원이 정지면 401 UNAUTHORIZED(다시 입장). 학원 없는 토큰은 활성
     * 운영자만(ADR 0032) — 학원은 null
     */
    public MeResponse me(long userId, Long academyId) {
        if (academyId == null) {
            AuthUser operator = preAuthUsers
                    .findActiveUser(userId)
                    .filter(AuthUser::isOperator)
                    .orElseThrow(() -> new ApiException(CommonErrorCode.UNAUTHORIZED));
            return new MeResponse(
                    operator.id(), operator.name(), operator.role().value(), null);
        }
        User user = users.findByAcademyIdAndId(academyId, userId)
                .filter(User::isActive)
                // 학원이 붙은 superadmin(데이터 오류)은 운영자도 학원 계정도 아니다 — 로그인·refresh와 같은 기준
                .filter(found -> found.getRole() != UserRole.SUPERADMIN)
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
