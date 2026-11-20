package com.jglearning.jg4seasons.auth.ratelimit;

import com.jglearning.jg4seasons.auth.AuthErrorCode;
import com.jglearning.jg4seasons.infra.web.error.ApiException;
import java.time.Instant;
import java.util.Optional;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 가입 코드 찍기 한도(없는 가입 코드만 IP당 1분 10회 — ADR 0033 보강). 이름 있는 빈이라 다른 카운터와 섞여 주입되지 않는다.
 *
 * <p>가입 코드 확인(1.1)과 학생 로그인(1.2)이 같은 칸을 쓴다. 맞는 코드는 세지 않아서 교실(공인 IP 하나)에서 한 반이 함께 들어와도 걸리지 않는다.
 */
public class CodeGuessLimiter {

    private static final Logger log = LoggerFactory.getLogger(CodeGuessLimiter.class);

    private final FixedWindowCounter counter;

    CodeGuessLimiter(FixedWindowCounter counter) {
        this.counter = counter;
    }

    /**
     * 가입 코드 조회를 한도로 감싼다. 한도가 찬 IP면 조회하지 않고 429 RATE_LIMITED — 맞는 코드도 같다(그러지 않으면 한도를 넘긴 쪽이 429와 그 밖의
     * 응답으로 코드가 있는지 알 수 있다). 조회 결과가 비면 한 번 세고 그대로 돌려준다. 조회하는 사이 다른 요청이 한도를 채웠으면 429.
     *
     * <p>429에는 풀리는 시각(확장 필드 until = 창 끝)을 싣는다. IP 칸의 값이라 코드가 있든 없든 같다. 한도가 차는 순간 WARN을 한 번 남긴다 — 그 뒤
     * 요청은 조회 전에 막혀 여기까지 오지 않는다. 그 IP의 교실 전체가 창 끝까지 막힌다는 뜻이라 운영에서 볼 수 있어야 한다. IP는 남기지 않는다.
     */
    public <T> Optional<T> guarded(String clientIp, Supplier<Optional<T>> lookup) {
        counter.exhaustedUntil(clientIp).ifPresent(until -> {
            throw rateLimited(until);
        });
        Optional<T> found = lookup.get();
        if (found.isEmpty()) {
            if (!counter.tryAcquire(clientIp)) {
                // 세지 못한 직후 창이 끝났으면 until이 없다(다음 요청은 통과한다)
                throw rateLimited(counter.exhaustedUntil(clientIp).orElse(null));
            }
            counter.exhaustedUntil(clientIp)
                    .ifPresent(until -> log.warn(
                            "code guess limit reached until={} — verify and student login from this client get 429"
                                    + " until then, valid codes included",
                            until));
        }
        return found;
    }

    private static ApiException rateLimited(Instant until) {
        ApiException limited = new ApiException(AuthErrorCode.RATE_LIMITED, "잠시 후 다시 시도해 주세요");
        return until == null ? limited : limited.with("until", until);
    }
}
