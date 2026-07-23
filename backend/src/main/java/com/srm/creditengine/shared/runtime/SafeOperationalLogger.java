package com.srm.creditengine.shared.runtime;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Locale;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.stereotype.Component;

/** Emits a deliberately small structured operational event schema. */
@Component
public class SafeOperationalLogger {
    private static final Logger LOG = LoggerFactory.getLogger(SafeOperationalLogger.class);
    private static final Pattern METHOD = Pattern.compile("[A-Z]{3,16}");
    private static final Pattern ROUTE = Pattern.compile("[/A-Za-z0-9_{}*.-]{1,160}");
    private static final Pattern CORRELATION = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,63}");
    private static final Pattern ERROR_TYPE = Pattern.compile("[A-Za-z0-9_$]{1,100}");

    public void requestCompleted(HttpServletRequest request, int status) {
        LOG.atInfo().addKeyValue("event", "HTTP_REQUEST_COMPLETED")
                .addKeyValue("method", safeMethod(request.getMethod()))
                .addKeyValue("route", safeRoute(request))
                .addKeyValue("status_class", statusClass(status))
                .addKeyValue("actor_role", actorRole())
                .addKeyValue("correlation_id", safeCorrelationId(MDC.get("correlationId")))
                .log("HTTP_REQUEST_COMPLETED");
    }

    public void financialConflict() {
        String role = actorRole();
        LOG.atWarn().addKeyValue("event", "FINANCIAL_CONFLICT")
                .addKeyValue("actor_role", role)
                .addKeyValue("outcome", "CONFLICT")
                .log("FINANCIAL_CONFLICT");
    }

    public void unexpectedFailure(Throwable failure) {
        String simpleName = failure == null ? null : failure.getClass().getSimpleName();
        String errorType = simpleName != null && ERROR_TYPE.matcher(simpleName).matches()
                ? simpleName
                : "UNKNOWN";
        LOG.atError().addKeyValue("event", "UNEXPECTED_API_FAILURE")
                .addKeyValue("error_type", errorType)
                .addKeyValue("correlation_id", safeCorrelationId(MDC.get("correlationId")))
                .log("UNEXPECTED_API_FAILURE");
    }

    private static String actorRole() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) return "ANONYMOUS";
        return authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority)
                .filter(authority -> authority.startsWith("ROLE_"))
                .map(authority -> authority.substring("ROLE_".length()))
                .sorted().findFirst().filter(role -> role.matches("[A-Z_]{2,32}")).orElse("ANONYMOUS");
    }

    private static String safeMethod(String method) {
        if (method == null) return "OTHER";
        String uppercase = method.toUpperCase(Locale.ROOT);
        return METHOD.matcher(uppercase).matches() ? uppercase : "OTHER";
    }

    private static String safeRoute(HttpServletRequest request) {
        Object route = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        return route instanceof String value && ROUTE.matcher(value).matches() ? value : "UNMATCHED";
    }

    private static String statusClass(int status) {
        if (status >= 500) return "5XX";
        if (status >= 400) return "4XX";
        return "2XX";
    }

    private static String safeCorrelationId(String value) {
        return value != null && CORRELATION.matcher(value).matches() ? value : "UNAVAILABLE";
    }
}
