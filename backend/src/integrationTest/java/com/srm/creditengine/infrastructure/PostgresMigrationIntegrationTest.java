package com.srm.creditengine.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest
class PostgresMigrationIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("srm_credit_engine")
            .withUsername("srm")
            .withPassword("srm");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("srm.jwt-secret", () -> "srm-test-secret-do-not-use-32-bytes-minimum");
    }

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void postgresAppliesFlywayMigrationsAndValidatesSchema() throws Exception {
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement();
                var rows = statement.executeQuery("select count(*) from flyway_schema_history")) {
            rows.next();
            assertThat(rows.getInt(1)).isGreaterThanOrEqualTo(6);
        }
    }

    @Test
    void auditEventsRejectsUpdateAndDelete() {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into audit_events (id,actor,action,target_type,target_id,occurred_at,safe_metadata) values (?,?,?,?,?,?,?::jsonb)",
                id, "operator@srm.local", "SETTLEMENT_CREATED", "SETTLEMENT", UUID.randomUUID(), Timestamp.from(Instant.now()), "{}");

        assertThatThrownBy(() -> jdbc.update("update audit_events set actor='tampered' where id=?", id))
                .hasMessageContaining("immutable");
        assertThatThrownBy(() -> jdbc.update("delete from audit_events where id=?", id))
                .hasMessageContaining("immutable");

        Integer count = jdbc.queryForObject("select count(*) from audit_events where id=?", Integer.class, id);
        assertThat(count).isEqualTo(1);
    }
}
