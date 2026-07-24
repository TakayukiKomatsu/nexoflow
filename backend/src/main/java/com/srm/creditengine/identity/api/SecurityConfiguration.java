package com.srm.creditengine.identity.api;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.srm.creditengine.shared.api.SecurityProblemWriter;
import com.srm.creditengine.shared.runtime.AuthenticatedRequestLogFilter;
import com.srm.creditengine.shared.runtime.SafeOperationalLogger;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
class SecurityConfiguration {
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    Clock applicationClock(@Value("${srm.clock.fixed-instant:}") String fixedInstant) {
        return fixedInstant.isBlank()
                ? Clock.systemUTC()
                : Clock.fixed(java.time.Instant.parse(fixedInstant), java.time.ZoneOffset.UTC);
    }

    @Bean
    SecretKey jwtKey(@Value("${srm.jwt-secret}") String secret) {
        byte[] key = secret.getBytes(StandardCharsets.UTF_8);
        if (key.length < 32) {
            throw new IllegalStateException("SRM_JWT_SECRET must contain at least 32 bytes");
        }
        return new SecretKeySpec(key, "HmacSHA256");
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey key) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(key));
    }

    @Bean
    JwtDecoder jwtDecoder(SecretKey key) {
        return NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
    }

    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        var authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("roles");
        authorities.setAuthorityPrefix("ROLE_");
        var converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }

    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationConverter jwtAuthenticationConverter,
            SecurityProblemWriter problems,
            SafeOperationalLogger operationalLogger)
            throws Exception {
        var authenticatedRequestLogFilter = new AuthenticatedRequestLogFilter(operationalLogger);
        return http.csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/api/v1/auth/login",
                                "/actuator/health/**",
                                "/v3/api-docs/**")
                        .permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/exchange-rates", "/api/v1/base-rates", "/api/v1/product-spreads", "/api/v1/fx-sync")
                        .hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/v1/exchange-rates", "/api/v1/base-rates", "/api/v1/product-spreads")
                        .hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/v1/conversions")
                        .hasAnyRole("OPERATOR", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/assignors", "/api/v1/receivables", "/api/v1/pricing-simulations", "/api/v1/pricing-quotes", "/api/v1/settlement-previews", "/api/v1/settlements")
                        .hasAnyRole("OPERATOR", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/settlements/*/reversals").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/v1/settlement-statements").hasAnyRole("OPERATOR", "ANALYST", "ADMIN", "AUDITOR")
                        .requestMatchers(HttpMethod.GET, "/api/v1/audit-events").hasAnyRole("ADMIN", "AUDITOR")
                        .requestMatchers(HttpMethod.GET, "/api/v1/assignors/**", "/api/v1/receivables/**", "/api/v1/pricing-quotes/**")
                        .hasAnyRole("OPERATOR", "ANALYST", "ADMIN", "AUDITOR")
                        .requestMatchers(HttpMethod.GET, "/api/v1/settlements/**")
                        .hasAnyRole("OPERATOR", "ANALYST", "ADMIN", "AUDITOR")
                        .requestMatchers(HttpMethod.GET, "/api/v1/users/me")
                        .authenticated()
                        .requestMatchers(HttpMethod.GET, "/actuator/prometheus")
                        .hasRole("ADMIN")
                        .anyRequest()
                        .denyAll())
                .oauth2ResourceServer(oauth -> oauth
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
                        .authenticationEntryPoint((request, response, exception) -> problems.write(
                                request, response, 401, "AUTHENTICATION_REQUIRED", "Authentication is required.")))
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, exception) -> problems.write(
                                request, response, 401, "AUTHENTICATION_REQUIRED", "Authentication is required."))
                        .accessDeniedHandler((request, response, exception) -> problems.write(
                                request, response, 403, "ACCESS_DENIED", "Access is denied.")))
                .addFilterAfter(authenticatedRequestLogFilter, BearerTokenAuthenticationFilter.class)
                .build();
    }
}
