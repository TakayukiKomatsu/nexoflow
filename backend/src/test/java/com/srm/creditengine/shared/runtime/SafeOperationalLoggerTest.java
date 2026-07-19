package com.srm.creditengine.shared.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerMapping;

class SafeOperationalLoggerTest {
    private final Logger logger = (Logger) LoggerFactory.getLogger(SafeOperationalLogger.class);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    @BeforeEach
    void captureLogs() {
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void clearLoggingContext() {
        logger.detachAppender(appender);
        appender.stop();
        SecurityContextHolder.clearContext();
        MDC.clear();
    }

    @Test
    void completionEventContainsOnlyBoundedOperationalFields() {
        var authentication = new UsernamePasswordAuthenticationToken(
                "reviewer@srm.local",
                "bearer-token-must-not-appear",
                java.util.List.of(
                        new SimpleGrantedAuthority("ROLE_OPERATOR"),
                        new SimpleGrantedAuthority("ROLE_ADMIN")));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        MDC.put("correlationId", "safe-correlation-001");
        var request = new MockHttpServletRequest("get", "/api/v1/settlements/71f847fd-9612-4dac-8a86-824896e8d5db");
        request.setAttribute(
                HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE,
                "/api/v1/settlements/{settlementId}");

        new SafeOperationalLogger().requestCompleted(request, 201);

        assertThat(appender.list).hasSize(1);
        var event = appender.list.getFirst();
        Map<String, String> fields = event.getKeyValuePairs().stream()
                .collect(Collectors.toMap(pair -> pair.key, pair -> String.valueOf(pair.value)));
        assertThat(fields).containsExactlyInAnyOrderEntriesOf(Map.of(
                "event", "HTTP_REQUEST_COMPLETED",
                "method", "GET",
                "route", "/api/v1/settlements/{settlementId}",
                "status_class", "2XX",
                "actor_role", "ADMIN",
                "correlation_id", "safe-correlation-001"));
        assertThat(event.getFormattedMessage())
                .doesNotContain("reviewer@srm.local")
                .doesNotContain("bearer-token-must-not-appear")
                .doesNotContain("71f847fd-9612-4dac-8a86-824896e8d5db");
    }

    @Test
    void unsafeRouteAndUnclassifiedStatusAreReducedToBoundedValues() {
        var request = new MockHttpServletRequest("get-with-secret", "/raw/credential/value");
        request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/raw/{id}?token={token}");

        new SafeOperationalLogger().requestCompleted(request, 302);

        Map<String, String> fields = appender.list.getFirst().getKeyValuePairs().stream()
                .collect(Collectors.toMap(pair -> pair.key, pair -> String.valueOf(pair.value)));
        assertThat(fields)
                .containsEntry("method", "OTHER")
                .containsEntry("route", "UNMATCHED")
                .containsEntry("status_class", "2XX")
                .containsEntry("actor_role", "ANONYMOUS");
    }
}
