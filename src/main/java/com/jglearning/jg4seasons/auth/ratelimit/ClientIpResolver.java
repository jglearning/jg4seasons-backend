package com.jglearning.jg4seasons.auth.ratelimit;

import com.jglearning.jg4seasons.auth.AuthProperties;
import jakarta.servlet.http.HttpServletRequest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * 요청 한도·찍기 방지에 쓰는 클라이언트 IP.
 *
 * <p>jg4seasons.auth.client-ip-header가 비어 있으면 연결 주소(getRemoteAddr)만 쓴다. X-Forwarded-For 같은 헤더는 누구나 보낼 수 있어
 * 설정하지 않으면 믿지 않는다. 운영은 앞단 프록시가 덮어쓰는 헤더 하나를 지정한다. 쉼표 목록이면 첫 값, `IPv4:포트`·`[IPv6]:포트`는 포트를 뗀다.
 * 대괄호 없는 IPv6는 포트와 구분할 수 없어 그대로 쓴다 — 프록시는 포트 없는 IP를 넘겨야 한다.
 */
@Component
public class ClientIpResolver {

    private static final Pattern IPV4_WITH_PORT = Pattern.compile("^(\\d{1,3}(?:\\.\\d{1,3}){3}):\\d+$");
    private static final Pattern BRACKETED_IPV6 = Pattern.compile("^\\[([0-9A-Fa-f:.]+)](?::\\d+)?$");

    private final String header;

    public ClientIpResolver(AuthProperties properties) {
        this.header = properties.clientIpHeader();
    }

    public String resolve(HttpServletRequest request) {
        if (header != null) {
            String value = request.getHeader(header);
            if (value != null && !value.isBlank()) {
                return normalize(value.split(",", 2)[0].trim());
            }
        }
        return request.getRemoteAddr();
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
