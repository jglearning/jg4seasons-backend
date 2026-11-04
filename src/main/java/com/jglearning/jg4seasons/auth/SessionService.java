package com.jglearning.jg4seasons.auth;

import com.jglearning.jg4seasons.academy.api.AcademyDirectory;
import com.jglearning.jg4seasons.auth.persistence.RefreshOwner;
import com.jglearning.jg4seasons.auth.token.IssuedTokens;
import com.jglearning.jg4seasons.auth.token.JwtIssuer;
import com.jglearning.jg4seasons.auth.token.RandomTokens;
import com.jglearning.jg4seasons.auth.token.RefreshOutcome;
import com.jglearning.jg4seasons.auth.token.RefreshTokenService;
import com.jglearning.jg4seasons.auth.web.dto.MeResponse;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 1.5 refresh · 1.6 logout. */
@Service
@Transactional
public class SessionService {

    /**
     * refresh 결과. 성공이면 body가 있고, 회전했으면 tokens(새 쿠키)도 있다. 유예 안 재사용이면 tokens가 비어 있다(다른 탭이 받은 쿠키를 같이 쓴다).
     * 거부면 body·tokens 모두 비어 있다.
     */
    public record RefreshResult(Optional<MeResponse> body, Optional<IssuedTokens> tokens) {

        static RefreshResult rejected() {
            return new RefreshResult(Optional.empty(), Optional.empty());
        }
    }

    private final RefreshTokenService refreshTokens;
    private final JwtIssuer jwtIssuer;
    private final AcademyDirectory academies;

    SessionService(RefreshTokenService refreshTokens, JwtIssuer jwtIssuer, AcademyDirectory academies) {
        this.refreshTokens = refreshTokens;
        this.jwtIssuer = jwtIssuer;
        this.academies = academies;
    }

    public RefreshResult refresh(String presented) {
        if (presented == null || presented.isBlank()) {
            return RefreshResult.rejected();
        }
        RefreshOutcome outcome = refreshTokens.rotate(presented);
        if (outcome instanceof RefreshOutcome.Rotated rotated && rotated.owner().academyId() != null) {
            RefreshOwner owner = rotated.owner();
            IssuedTokens tokens = new IssuedTokens(
                    jwtIssuer.issueAccess(owner.id(), owner.role(), owner.academyId()),
                    rotated.newRefresh(),
                    RandomTokens.next(),
                    rotated.refreshTtl());
            return new RefreshResult(Optional.of(me(owner)), Optional.of(tokens));
        }
        if (outcome instanceof RefreshOutcome.GraceReuse grace && grace.owner().academyId() != null) {
            return new RefreshResult(Optional.of(me(grace.owner())), Optional.empty());
        }
        // 거부, 또는 학원이 없는 사용자(시스템 관리자 — 1차는 학생만 refresh)
        return RefreshResult.rejected();
    }

    /** 이 refresh를 유예 없이 끊는다. 쿠키가 없거나 모르는 값이면 아무것도 하지 않는다 */
    public void logout(String presented) {
        if (presented != null && !presented.isBlank()) {
            refreshTokens.revoke(presented);
        }
    }

    private MeResponse me(RefreshOwner owner) {
        return new MeResponse(
                owner.id(),
                owner.name(),
                owner.role(),
                new MeResponse.AcademyRef(owner.academyId(), academies.nameOf(owner.academyId())));
    }
}
