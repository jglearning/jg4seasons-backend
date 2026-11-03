package com.jglearning.jg4seasons.infra.web.requestid;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/** 요청 처리 중에는 MDC·요청 속성에 같은 ID가 있고, 끝나면 MDC가 비워진다(스레드 재사용 시 다른 요청에 섞이지 않게). */
class RequestIdFilterTest {

    @Test
    void 요청_중에는_MDC와_요청_속성과_헤더가_같고_끝나면_MDC가_비워진다() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/any");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> inMdc = new AtomicReference<>();
        AtomicReference<String> inAttribute = new AtomicReference<>();
        MockFilterChain chain = new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                inMdc.set(MDC.get(RequestIds.MDC_KEY));
                inAttribute.set(RequestIds.current((jakarta.servlet.http.HttpServletRequest) req));
            }
        };

        new RequestIdFilter().doFilter(request, response, chain);

        assertThat(inMdc.get()).isNotNull().isEqualTo(inAttribute.get());
        assertThat(response.getHeader(RequestIds.HEADER)).isEqualTo(inMdc.get());
        assertThat(MDC.get(RequestIds.MDC_KEY)).isNull();
    }
}
