package com.srm.creditengine.shared.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.srm.creditengine.shared.runtime.SafeOperationalLogger;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CorrelationIdFilterTest {
    @Test
    void OBS_003_rejectsCredentialShapedCorrelationHeaderAndReturnsSafeGeneratedValue() throws Exception {
        var filter = new CorrelationIdFilter(new SafeOperationalLogger());
        var request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.HEADER, "eyJhbGciOiJIUzI1NiJ9." + "x".repeat(120));
        var response = new MockHttpServletResponse();
        var observed = new AtomicReference<String>();

        filter.doFilter(request, response, (servletRequest, servletResponse) ->
                observed.set((String) servletRequest.getAttribute(CorrelationIdFilter.ATTRIBUTE)));

        assertThat(observed.get()).matches("[0-9a-f-]{36}");
        assertThat(response.getHeader(CorrelationIdFilter.HEADER)).isEqualTo(observed.get());
    }

    @Test
    void correlationContextExistsOnlyWhileTheRequestIsBeingProcessed() throws Exception {
        var filter = new CorrelationIdFilter(new SafeOperationalLogger());
        var request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.HEADER, "safe-correlation-001");
        var observed = new AtomicReference<String>();

        filter.doFilter(request, new MockHttpServletResponse(), (servletRequest, servletResponse) ->
                observed.set(MDC.get("correlationId")));

        assertThat(observed.get()).isEqualTo("safe-correlation-001");
        assertThat(MDC.get("correlationId")).isNull();
    }
}
