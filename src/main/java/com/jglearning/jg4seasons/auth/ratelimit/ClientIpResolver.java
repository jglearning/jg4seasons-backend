package com.jglearning.jg4seasons.auth.ratelimit;

import com.jglearning.jg4seasons.auth.AuthProperties;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 요청 한도·찍기 방지에 쓰는 클라이언트 IP.
 *
 * <p>jg4seasons.auth.client-ip-header가 비어 있으면 연결 주소(getRemoteAddr)만 쓴다. X-Forwarded-For 같은 헤더는 누구나 보낼 수 있어
 * 설정하지 않으면 믿지 않는다. 운영은 앞단 프록시가 덮어쓰는 헤더 하나를 지정한다.
 *
 * <ul>
 *   <li>쉼표 목록이면 비어 있지 않은 마지막 값(가장 가까운 프록시가 붙인 값). 첫 값은 클라이언트가 보낸 값일 수 있다
 *   <li>`IPv4:포트`·`[IPv6]:포트`는 포트를 뗀다. 대괄호 없는 IPv6는 포트와 구분할 수 없어 그대로 — 프록시는 포트 없는 IP를 넘겨야 한다
 *   <li>설정한 헤더가 요청에 없으면 연결 주소(대개 프록시 하나)로 세게 되어 한 반 전체가 같이 잠길 수 있다 → 1분에 한 번 WARN
 * </ul>
 */
@Component
public class ClientIpResolver {

    static final Duration MISSING_HEADER_WARN_INTERVAL = Duration.ofMinutes(1);

    private static final Logger log = LoggerFactory.getLogger(ClientIpResolver.class);
    private static final Pattern IPV4_WITH_PORT = Pattern.compile("^(\\d{1,3}(?:\\.\\d{1,3}){3}):\\d+$");
    private static final Pattern BRACKETED_IPV6 = Pattern.compile("^\\[([0-9A-Fa-f:.]+)](?::\\d+)?$");

    private final Clock clock;
    private final String header;
    private final AtomicReference<Instant> lastMissingWarn = new AtomicReference<>();

    @Autowired
    public ClientIpResolver(Clock clock, AuthProperties properties) {
        this.clock = clock;
        this.header = properties.clientIpHeader();
    }

    public String resolve(HttpServletRequest request) {
        if (header == null) {
            return request.getRemoteAddr();
        }
        String value = request.getHeader(header);
        String[] hops = value == null ? new String[0] : value.split(",");
        for (int i = hops.length - 1; i >= 0; i--) {
            String hop = hops[i].trim();
            if (!hop.isEmpty()) {
                return normalize(hop);
            }
        }
        // 헤더가 없거나 빈 항목뿐 — 빈 문자열을 키로 쓰면 모두 한 칸에서 세진다
        warnMissingHeader(request);
        return request.getRemoteAddr();
    }

    private void warnMissingHeader(HttpServletRequest request) {
        Instant now = clock.instant();
        Instant last = lastMissingWarn.get();
        if ((last == null || !now.isBefore(last.plus(MISSING_HEADER_WARN_INTERVAL)))
                && lastMissingWarn.compareAndSet(last, now)) {
            log.warn(
                    "client ip header missing header={} remoteAddr={} — all requests count as one client",
                    header,
                    request.getRemoteAddr());
        }
    }

    private static String normalize(String ip) {
        Matcher v4 = IPV4_WITH_PORT.matcher(ip);
        if (v4.matches()) {
            return v4.group(1);
        }
        Matcher v6 = BRACKETED_IPV6.matcher(ip);
        if (v6.matches()) {
            return v6.group(1);
        }
        return ip;
    }
}
