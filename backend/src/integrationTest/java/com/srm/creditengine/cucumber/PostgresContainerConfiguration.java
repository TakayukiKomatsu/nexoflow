package com.srm.creditengine.cucumber;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.testcontainers.containers.PostgreSQLContainer;

/** Spring-managed PostgreSQL container for the shared Cucumber context.
 *  Do not annotate with @Container; Spring owns the lifecycle so the context is cached. */
@TestConfiguration(proxyBeanMethods = false)
class PostgresContainerConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgres() {
        return new PostgreSQLContainer<>("postgres:16-alpine");
    }

    /** Seeds deterministic users, the fixed FX rate, and the fixed Assignor once at context startup. */
    @Bean
    ApplicationRunner cucumberSeed(JdbcTemplate jdbc, PasswordEncoder passwords) {
        return args -> {
            seedUser(jdbc, passwords, "operator@srm.local", "P@ssword1", "OPERATOR");
            seedUser(jdbc, passwords, "admin@srm.local", "P@ssword1", "ADMIN");
            seedUser(jdbc, passwords, "analyst@srm.local", "P@ssword1", "ANALYST");

            Instant seed = Instant.parse("2030-01-15T12:00:00Z");

            // Fixed exchange rate USD/BRL = 5.2 (same UUID as e2e-fixtures to stay idempotent)
            jdbc.update(
                    "insert into exchange_rates"
                    + "(id,base_currency_code,quote_currency_code,rate,source,observed_at,created_at,created_by)"
                    + " values (?,?,?,?,?,?,?,?) on conflict (id) do nothing",
                    UUID.fromString("00000000-0000-0000-0000-000000000202"),
                    "USD", "BRL", new BigDecimal("5.2000000000"),
                    "cucumber-seed", Timestamp.from(seed), Timestamp.from(seed), "cucumber-seed");

            // Fixed Assignor (same UUID as e2e-fixtures)
            jdbc.update(
                    "insert into assignors(id,legal_name,normalized_tax_id,active,created_at,created_by)"
                    + " values (?,?,?,?,?,?) on conflict (id) do nothing",
                    UUID.fromString("00000000-0000-0000-0000-000000000201"),
                    "E2E Fixture Assignor", "FIXTURE-ASSIGNOR-001",
                    true, Timestamp.from(seed), "cucumber-seed");
        };
    }

    private void seedUser(JdbcTemplate jdbc, PasswordEncoder passwords,
            String email, String password, String role) {
        UUID id = UUID.randomUUID();
        int inserted = jdbc.update(
                "insert into users(id,email,password_hash,enabled) values (?,?,?,true)"
                + " on conflict (email) do nothing",
                id, email, passwords.encode(password));
        UUID userId = inserted == 1
                ? id
                : jdbc.queryForObject("select id from users where email=?", UUID.class, email);
        jdbc.update(
                "insert into user_roles(user_id,role) values (?,?) on conflict (user_id,role) do nothing",
                userId, role);
    }
}
