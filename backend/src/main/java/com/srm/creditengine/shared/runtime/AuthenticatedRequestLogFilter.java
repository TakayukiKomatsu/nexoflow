package com.srm.creditengine.shared.runtime;

import java.io.IOException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

/** Logs the bounded request completion event before Spring Security clears authentication. */
public final class AuthenticatedRequestLogFilter extends OncePerRequestFilter {
    private final SafeOperationalLogger operationalLogger;

    public AuthenticatedRequestLogFilter(SafeOperationalLogger operationalLogger) {
        this.operationalLogger = operationalLogger;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {
        try {
            filterChain.doFilter(request, response);
        } finally {
            operationalLogger.requestCompleted(request, response.getStatus());
        }
    }
}
