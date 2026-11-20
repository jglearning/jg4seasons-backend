package com.jglearning.jg4seasons.auth.ratelimit;

import com.jglearning.jg4seasons.auth.AuthErrorCode;
import com.jglearning.jg4seasons.infra.web.error.ApiException;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * 가입 코드 찍기 한도(없는 가입 코드만 IP당 1분 10회 — ADR 0033 보강). 이름 있는 빈이라 다른 카운터와 섞여 주입되지 않는다.
 *
 * <p>가입 코드 확인(1.1)과 학생 로그인(1.2)이 같은 칸을 쓴다. 맞는 코드는 세지 않아서 교실(공인 IP 하나)에서 한 반이 함께 들어와도 걸리지 않는다.
 */
public class CodeGuessLimiter {

    private final FixedWindowCounter counter;

    CodeGuessLimiter(FixedWindowCounter counter) {
        this.counter = counter;
    }

    /**
     * 가입 코드 조회를 한도로 감싼다. 한도가 찬 IP면 조회하지 않고 429 RATE_LIMITED — 맞는 코드도 같다(그러지 않으면 한도를 넘긴 쪽이 429와 그 밖의
     * 응답으로 코드가 있는지 알 수 있다). 조회 결과가 비면 한 번 세고 그대로 돌려준다. 조회하는 사이 다른 요청이 한도를 채웠으면 429.
     */
    public <T> Optional<T> guarded(String clientIp, Supplier<Optional<T>> lookup) {
        if (counter.isExhausted(clientIp)) {
            throw rateLimited();
        }
        Optional<T> found = lookup.get();
        if (found.isEmpty() && !counter.tryAcquire(clientIp)) {
            throw rateLimited();
        }
        return found;
    }

    private static ApiException rateLimited() {
        return new ApiException(AuthErrorCode.RATE_LIMITED, "잠시 후 다시 시도해 주세요");
    }
}
