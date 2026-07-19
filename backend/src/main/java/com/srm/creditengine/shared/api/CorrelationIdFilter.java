package com.srm.creditengine.shared.api;

import java.io.IOException;
import java.util.UUID;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.filter.OncePerRequestFilter;
import org.slf4j.MDC;
import com.srm.creditengine.shared.runtime.SafeOperationalLogger;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {
    public static final String HEADER = "X-Correlation-Id";
    public static final String ATTRIBUTE = CorrelationIdFilter.class.getName();
    private static final int MAX_CORRELATION_ID_LENGTH = 64;

    public CorrelationIdFilter(SafeOperationalLogger operationalLogger) {
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String correlationId = request.getHeader(HEADER);
        if (!isSafeCorrelationId(correlationId)) {
            correlationId = UUID.randomUUID().toString();
        }
        request.setAttribute(ATTRIBUTE, correlationId);
        response.setHeader(HEADER, correlationId);
        MDC.put("correlationId", correlationId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove("correlationId");
        }
    }

    private static boolean isSafeCorrelationId(String value) {
        return value != null && value.length() <= MAX_CORRELATION_ID_LENGTH
                && value.matches("[A-Za-z0-9][A-Za-z0-9._-]*");
    }
}
