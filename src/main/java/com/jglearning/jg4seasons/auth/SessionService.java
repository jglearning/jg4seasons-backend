package com.jglearning.jg4seasons.auth;

import com.jglearning.jg4seasons.academy.api.AcademyDirectory;
import com.jglearning.jg4seasons.auth.persistence.RefreshOwner;
import com.jglearning.jg4seasons.auth.token.IssuedTokens;
import com.jglearning.jg4seasons.auth.token.JwtIssuer;
import com.jglearning.jg4seasons.auth.token.RandomTokens;
import com.jglearning.jg4seasons.auth.token.RefreshOutcome;
import com.jglearning.jg4seasons.auth.token.RefreshTokenService;
import com.jglearning.jg4seasons.auth.web.dto.MeResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 1.5 refresh · 1.6 logout. */
@Service
@Transactional
public class SessionService {

    /** refresh 결과. 만들 수 있는 조합은 이 셋뿐이다. */
    public sealed interface RefreshResult {

        /** 회전: 새 쿠키 3개와 내 정보 */
        record Rotated(MeResponse body, IssuedTokens tokens) implements RefreshResult {}

        /** 유예 안 재사용: 내 정보만, 쿠키는 그대로(다른 탭이 받은 쿠키를 같이 쓴다) */
        record Reused(MeResponse body) implements RefreshResult {}

        /** 거부: 401 + 쿠키 만료 */
        record Rejected() implements RefreshResult {}
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
            return new RefreshResult.Rejected();
        }
        // 소유자(활성 사용자 + 학원 있음 + 정지 아님)는 RefreshTokenService가 회전 전에 확인한다
        RefreshOutcome outcome = refreshTokens.rotate(presented);
        if (outcome instanceof RefreshOutcome.Rotated rotated) {
            RefreshOwner owner = rotated.owner();
            IssuedTokens tokens = new IssuedTokens(
                    jwtIssuer.issueAccess(owner.id(), owner.role(), owner.academyId()),
                    rotated.newRefresh(),
                    RandomTokens.next(),
                    rotated.refreshTtl());
            return new RefreshResult.Rotated(me(owner), tokens);
        }
        if (outcome instanceof RefreshOutcome.GraceReuse grace) {
            return new RefreshResult.Reused(me(grace.owner()));
        }
        return new RefreshResult.Rejected();
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
