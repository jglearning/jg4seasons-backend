package com.jglearning.jg4seasons.auth.token;

import com.jglearning.jg4seasons.auth.persistence.AuthUser;
import java.time.Duration;

/** refresh 회전 결과. */
public sealed interface RefreshOutcome {

    /** 새 refresh 발급. 옛 토큰은 폐기(유예 판정용 시각 기록) */
    record Rotated(AuthUser owner, String newRefresh, Duration refreshTtl) implements RefreshOutcome {
        @Override
        public String toString() {
            return "Rotated[owner=" + owner.id() + "]";
        }
    }

    /** 회전된 지 유예 안의 옛 토큰(다른 탭의 동시 refresh). 새 토큰 없음 — 다른 탭이 받은 쿠키를 같이 쓴다 */
    record GraceReuse(AuthUser owner) implements RefreshOutcome {}

    /** 없음·만료·끊김·유예 뒤 재사용(이때 그 사용자 refresh 전부 끊음)·비활성 사용자·정지 학원 */
    record Rejected() implements RefreshOutcome {}
}
