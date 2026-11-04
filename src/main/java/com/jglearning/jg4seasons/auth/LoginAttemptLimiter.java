package com.jglearning.jg4seasons.auth;

import com.jglearning.jg4seasons.auth.api.LoginAlerts;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 개인 코드 찍기 방지(ADR-22, AUTH-07). 인메모리, 단일 인스턴스 전제.
 *
 * <ul>
 *   <li>(반, IP): 첫 실패부터 10분 안에 5회 실패 → 그 시각부터 10분 잠금. 잠긴 동안은 맞는 코드도 거부. 성공해도 그동안의 실패는 남는다
 *       (시도는 확인 전에 먼저 센다 — beginAttempt)
 *   <li>반 전체: 최근 1시간 실패 20회 이상이면 경고(교사 반 목록). 20회에 닿는 순간 WARN 로그 1회(반 id만)
 * </ul>
 *
 * <p>메모리: 키가 SWEEP_THRESHOLD를 넘으면 10분마다 최대 한 번 끝난 항목을 지운다. 정리 뒤에도 HARD_CAP에 차 있으면 새 IP는 반별 공용 칸에서 함께
 * 센다(요청 한도 카운터와 같은 방식). 서버를 2대 이상으로 늘리면 DB나 Redis로 옮긴다(ADR-22 재검토).
 */
@Component
public class LoginAttemptLimiter implements LoginAlerts {

    static final int MAX_FAILURES = 5;
    static final Duration FAILURE_WINDOW = Duration.ofMinutes(10);
    static final Duration LOCK = Duration.ofMinutes(10);
    static final int CLASS_ALERT_FAILURES = 20;
    static final Duration CLASS_ALERT_WINDOW = Duration.ofHours(1);

    static final int SWEEP_THRESHOLD = 10_000;
    static final int HARD_CAP = 100_000;
    private static final String OVERFLOW_IP = "\u0000overflow";

    private static final Logger log = LoggerFactory.getLogger(LoginAttemptLimiter.class);

    /** windowStart: 첫 실패 시각. lockedUntil: 잠겼으면 해제 시각 */
    private record Attempts(Instant windowStart, int failures, Instant lockedUntil) {

        boolean lockedAt(Instant now) {
            return lockedUntil != null && now.isBefore(lockedUntil);
        }

        boolean finishedAt(Instant now) {
            return !lockedAt(now) && !now.isBefore(windowStart.plus(FAILURE_WINDOW));
        }
    }

    private final Clock clock;
    private final int sweepThreshold;
    private final int hardCap;
    private final ConcurrentHashMap<String, Attempts> attempts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Deque<Instant>> classFailures = new ConcurrentHashMap<>();
    private volatile Instant lastSweep;

    @Autowired
    public LoginAttemptLimiter(Clock clock) {
        this(clock, SWEEP_THRESHOLD, HARD_CAP);
    }

    LoginAttemptLimiter(Clock clock, int sweepThreshold, int hardCap) {
        this.clock = clock;
        this.sweepThreshold = sweepThreshold;
        this.hardCap = hardCap;
    }

    /** 잠겨 있으면 해제 시각 */
    public Optional<Instant> lockedUntil(long classId, String clientIp) {
        Instant now = clock.instant();
        Attempts current = attempts.get(key(classId, clientIp));
        return current != null && current.lockedAt(now) ? Optional.of(current.lockedUntil()) : Optional.empty();
    }

    /**
     * 시도 시작. 확인(HMAC 조회) 전에 먼저 실패 1회로 센다 — 잠금 검사와 실패 기록 사이에 동시 요청이 끼어 5번보다 많이 찍지 못하게. 5번째 시도를 세는
     * 순간 잠금을 건다(맞으면 recordSuccess가 되돌림).
     *
     * @return 이미 잠겨 있으면 해제 시각(확인하지 말고 거부). 비어 있으면 확인해도 된다
     */
    public Optional<Instant> beginAttempt(long classId, String clientIp) {
        Instant now = clock.instant();
        if (attempts.size() > sweepThreshold) {
            sweepIfDue(now);
        }
        boolean[] rejected = {false};
        Attempts updated = attempts.compute(key(classId, clientIp), (k, current) -> {
            if (current == null || current.finishedAt(now)) {
                return new Attempts(now, 1, null);
            }
            if (current.lockedAt(now)) {
                rejected[0] = true;
                return current;
            }
            int failures = current.failures() + 1;
            return new Attempts(current.windowStart(), failures, failures >= MAX_FAILURES ? now.plus(LOCK) : null);
        });
        return rejected[0] ? Optional.of(updated.lockedUntil()) : Optional.empty();
    }

    /** 확인 결과 틀림. 반 전체 실패를 센다. 이 시도로 잠겼으면 해제 시각 */
    public Optional<Instant> recordFailure(long classId, String clientIp) {
        recordClassFailure(classId, clock.instant());
        return lockedUntil(classId, clientIp);
    }

    /**
     * 확인 결과 맞음. 시작할 때 센 1회만 되돌린다(그 시도로 건 잠금도 풀림). 그동안의 실패는 지우지 않는다 — 자기 코드로 성공해 횟수를 지우며 남의 코드를
     * 계속 찍지 못하게. 남은 실패는 10분 창이 지나면 사라진다.
     */
    public void recordSuccess(long classId, String clientIp) {
        attempts.computeIfPresent(key(classId, clientIp), (k, current) -> {
            int failures = current.failures() - 1;
            if (failures <= 0) {
                return null;
            }
            return new Attempts(
                    current.windowStart(), failures, failures >= MAX_FAILURES ? current.lockedUntil() : null);
        });
    }

    @Override
    public boolean isAlerting(long classId) {
        Deque<Instant> failures = classFailures.get(classId);
        if (failures == null) {
            return false;
        }
        synchronized (failures) {
            prune(failures, clock.instant());
            return failures.size() >= CLASS_ALERT_FAILURES;
        }
    }

    private void recordClassFailure(long classId, Instant now) {
        Deque<Instant> failures = classFailures.computeIfAbsent(classId, id -> new ArrayDeque<>());
        synchronized (failures) {
            prune(failures, now);
            boolean wasAlerting = failures.size() >= CLASS_ALERT_FAILURES;
            failures.addLast(now);
            while (failures.size() > CLASS_ALERT_FAILURES) {
                failures.removeFirst(); // 최근 20개만 있으면 판정할 수 있다
            }
            if (!wasAlerting && failures.size() >= CLASS_ALERT_FAILURES) {
                log.warn("login failures over threshold classId={}", classId);
            }
        }
    }

    private static void prune(Deque<Instant> failures, Instant now) {
        Instant from = now.minus(CLASS_ALERT_WINDOW);
        while (!failures.isEmpty() && !failures.peekFirst().isAfter(from)) {
            failures.removeFirst();
        }
    }

    /** 상한에 차 있으면 처음 보는 IP는 그 반의 공용 칸 */
    private String key(long classId, String clientIp) {
        String own = classId + "|" + clientIp;
        return !attempts.containsKey(own) && attempts.size() >= hardCap ? classId + "|" + OVERFLOW_IP : own;
    }

    private synchronized void sweepIfDue(Instant now) {
        if (lastSweep != null && now.isBefore(lastSweep.plus(FAILURE_WINDOW))) {
            return;
        }
        lastSweep = now;
        attempts.values().removeIf(a -> a.finishedAt(now));
        if (attempts.size() >= hardCap) {
            log.warn("login attempt keys at hard cap size={} — new IPs share a per-class bucket", attempts.size());
        }
    }
}
