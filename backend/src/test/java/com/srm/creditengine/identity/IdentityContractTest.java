package com.srm.creditengine.identity;

import static org.assertj.core.api.Assertions.assertThat;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.time.Clock;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import com.srm.creditengine.shared.runtime.SafeOperationalLogger;

@SpringBootTest(properties = {
        "management.endpoints.web.exposure.include=health,info,prometheus",
        "management.prometheus.metrics.export.enabled=true"
})
@AutoConfigureMockMvc
class IdentityContractTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PasswordEncoder passwords;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JwtEncoder jwtEncoder;
    @Autowired private Clock clock;

    @BeforeEach
    void seedOperator() {
        jdbc.update("delete from user_roles");
        jdbc.update("delete from users");
        UUID id = UUID.fromString("00000000-0000-0000-0000-000000000301");
        jdbc.update(
                "insert into users(id,email,password_hash,enabled) values (?,?,?,true)",
                id,
                "operator@srm.local",
                passwords.encode("correct-password"));
        jdbc.update("insert into user_roles(user_id,role) values (?,?)", id, "OPERATOR");
    }

    @Test
    void invalidCredentialsReturnGenericUnauthorizedProblem() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"missing@srm.local\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.correlationId").isNotEmpty())
                .andExpect(header().exists("X-Correlation-Id"));
    }

    @Test
    void issuedTokenAuthenticatesCurrentUser() throws Exception {
        String response = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"operator@srm.local\",\"password\":\"correct-password\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String token = objectMapper.readTree(response).get("accessToken").asText();

        mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("operator@srm.local"))
                .andExpect(jsonPath("$.roles[0]").value("OPERATOR"));
    }

    @Test
    void malformedTokenReturnsControlledProblem() throws Exception {
        mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer malformed"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
                .andExpect(jsonPath("$.correlationId").isNotEmpty());
    }

    @Test
    void operatorCannotMutateExchangeRates() throws Exception {
        String response = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"operator@srm.local\",\"password\":\"correct-password\"}"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String token = objectMapper.readTree(response).get("accessToken").asText();

        mockMvc.perform(post("/api/v1/exchange-rates")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void repeatedLoginFailuresAreRateLimited() throws Exception {
        for (int attempt = 0; attempt < 5; attempt++) {
            mockMvc.perform(post("/api/v1/auth/login")
                            .with(request -> {
                                request.setRemoteAddr("192.0.2.10");
                                return request;
                            })
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"rate-limit@srm.local\",\"password\":\"wrong\"}"))
                    .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .with(request -> {
                            request.setRemoteAddr("192.0.2.10");
                            return request;
                        })
                        .content("{\"email\":\"rate-limit@srm.local\",\"password\":\"wrong\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("LOGIN_RATE_LIMITED"));
    }

    @Test
    void failuresFromOneNetworkSourceCannotLockTheIdentityFromAnotherSource() throws Exception {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "insert into users(id,email,password_hash,enabled) values (?,?,?,true)",
                id,
                "source-isolated@srm.local",
                passwords.encode("correct-password"));
        jdbc.update("insert into user_roles(user_id,role) values (?,?)", id, "OPERATOR");

        for (int attempt = 0; attempt < 5; attempt++) {
            login("source-isolated@srm.local", "wrong", "192.0.2.40")
                    .andExpect(status().isUnauthorized());
        }
        login("source-isolated@srm.local", "wrong", "192.0.2.40")
                .andExpect(status().isTooManyRequests());

        login("source-isolated@srm.local", "correct-password", "192.0.2.41")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
    }

    @ParameterizedTest(name = "{0} against {1}")
    @MethodSource("permissionMatrixCases")
    void permissionMatrixMatchesEveryConfiguredRouteClass(String role, PermissionRule rule) throws Exception {
        var request = request(HttpMethod.valueOf(rule.method()), rule.path())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}");
        if (role != null) {
            request.header("Authorization", "Bearer " + issueTokenFor(role));
        }

        int actualStatus = mockMvc.perform(request).andReturn().getResponse().getStatus();
        if (role == null) {
            assertThat(actualStatus).isEqualTo(401);
        } else if (rule.allowedRoles().contains(role)) {
            assertThat(actualStatus).isNotIn(401, 403);
        } else {
            assertThat(actualStatus).isEqualTo(403);
        }
    }

    static Stream<Arguments> permissionMatrixCases() {
        List<String> roles = java.util.Arrays.asList(null, "OPERATOR", "ANALYST", "AUDITOR", "ADMIN");
        List<PermissionRule> rules = List.of(
                new PermissionRule("POST", "/api/v1/exchange-rates", Set.of("ADMIN")),
                new PermissionRule("GET", "/api/v1/exchange-rates", Set.of("ADMIN")),
                new PermissionRule("GET", "/api/v1/conversions", Set.of("OPERATOR", "ADMIN")),
                new PermissionRule("POST", "/api/v1/assignors", Set.of("OPERATOR", "ADMIN")),
                new PermissionRule(
                        "POST",
                        "/api/v1/settlements/71f847fd-9612-4dac-8a86-824896e8d5db/reversals",
                        Set.of("ADMIN")),
                new PermissionRule(
                        "GET",
                        "/api/v1/settlement-statements",
                        Set.of("OPERATOR", "ANALYST", "AUDITOR", "ADMIN")),
                new PermissionRule("GET", "/api/v1/audit-events", Set.of("AUDITOR", "ADMIN")),
                new PermissionRule(
                        "GET",
                        "/api/v1/assignors",
                        Set.of("OPERATOR", "ANALYST", "AUDITOR", "ADMIN")),
                new PermissionRule(
                        "GET",
                        "/api/v1/settlements/71f847fd-9612-4dac-8a86-824896e8d5db",
                        Set.of("OPERATOR", "ANALYST", "AUDITOR", "ADMIN")),
                new PermissionRule(
                        "GET",
                        "/api/v1/users/me",
                        Set.of("OPERATOR", "ANALYST", "AUDITOR", "ADMIN")));
        return rules.stream().flatMap(rule -> roles.stream().map(role -> Arguments.of(role, rule)));
    }

    record PermissionRule(String method, String path, Set<String> allowedRoles) {
        @Override
        public String toString() {
            return method + " " + path;
        }
    }

    @ParameterizedTest(name = "{0} receives {1} from Prometheus")
    @MethodSource("prometheusAuthorizationCases")
    void prometheusAuthorizationMatrix(String role, int expectedStatus) throws Exception {
        var request = get("/actuator/prometheus");
        if (role != null) {
            request.header("Authorization", "Bearer " + issueTokenFor(role));
        }

        mockMvc.perform(request).andExpect(status().is(expectedStatus));
    }

    static Stream<Arguments> prometheusAuthorizationCases() {
        return Stream.of(
                Arguments.of(null, 401),
                Arguments.of("OPERATOR", 403),
                Arguments.of("ADMIN", 200));
    }

    @ParameterizedTest(name = "{0} is denied an unmatched route with {1}")
    @MethodSource("denyByDefaultCases")
    void unmatchedRoutesAreDeniedByDefault(String role, int expectedStatus) throws Exception {
        var request = get("/api/v1/not-a-real-resource");
        if (role != null) {
            request.header("Authorization", "Bearer " + issueTokenFor(role));
        }

        mockMvc.perform(request).andExpect(status().is(expectedStatus));
    }

    static Stream<Arguments> denyByDefaultCases() {
        return Stream.of(
                Arguments.of(null, 401),
                Arguments.of("OPERATOR", 403),
                Arguments.of("ADMIN", 403));
    }

    @Test
    void authenticatedCompletionLogContainsRoleButNoCredentialsOrIdentifiers() throws Exception {
        String token = issueTokenFor("ADMIN");
        Logger logger = (Logger) LoggerFactory.getLogger(SafeOperationalLogger.class);
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        String recordId = "71f847fd-9612-4dac-8a86-824896e8d5db";
        try {
            mockMvc.perform(get("/api/v1/settlements/" + recordId)
                    .header("Authorization", "Bearer " + token));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }

        ILoggingEvent completion = appender.list.stream()
                .filter(event -> event.getFormattedMessage().equals("HTTP_REQUEST_COMPLETED"))
                .findFirst()
                .orElseThrow();
        assertThat(completion.getKeyValuePairs())
                .anySatisfy(field -> {
                    assertThat(field.key).isEqualTo("actor_role");
                    assertThat(field.value).isEqualTo("ADMIN");
                });
        String renderedEvent = completion.getFormattedMessage() + completion.getKeyValuePairs();
        assertThat(renderedEvent)
                .doesNotContain("operator@srm.local")
                .doesNotContain("correct-password")
                .doesNotContain(token)
                .doesNotContain(recordId)
                .doesNotContain("Idempotency-Key");
    }

    private String issueTokenFor(String role) {
        var issuedAt = clock.instant();
        var claims = JwtClaimsSet.builder()
                .issuer("srm-credit-engine")
                .subject("00000000-0000-0000-0000-000000000301")
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plusSeconds(900))
                .claim("email", "operator@srm.local")
                .claim("roles", List.of(role))
                .build();
        var header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    private org.springframework.test.web.servlet.ResultActions login(
            String email, String password, String source) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                .with(request -> {
                    request.setRemoteAddr(source);
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"));
    }
}
