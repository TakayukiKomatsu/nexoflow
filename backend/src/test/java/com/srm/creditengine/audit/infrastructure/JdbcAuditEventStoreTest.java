package com.srm.creditengine.audit.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.MDC;
import org.springframework.jdbc.core.JdbcTemplate;

class JdbcAuditEventStoreTest {

    @AfterEach
    void clearCorrelation() {
        MDC.clear();
    }

    @Test
    void ownsTheSharedAppendOnlyAuditRowShape() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        UUID targetId = UUID.fromString("00000000-0000-4000-8000-000000000051");
        Instant occurredAt = Instant.parse("2030-01-15T12:00:00Z");
        MDC.put("correlationId", "corr-51");

        new JdbcAuditEventStore(jdbc)
                .append(
                        "operator@srm.local",
                        "SETTLEMENT_CREATED",
                        "SETTLEMENT",
                        targetId,
                        occurredAt,
                        "{\"itemCount\":2}");

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> arguments = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).update(sql.capture(), arguments.capture());
        assertThat(sql.getValue())
                .isEqualTo("insert into audit_events "
                        + "(id,actor,action,target_type,target_id,occurred_at,correlation_id,safe_metadata) "
                        + "values (?,?,?,?,?,?,?,?::jsonb)");
        assertThat(arguments.getValue())
                .hasSize(8)
                .satisfies(values -> {
                    assertThat(values[0]).isInstanceOf(UUID.class);
                    assertThat(values[1]).isEqualTo("operator@srm.local");
                    assertThat(values[2]).isEqualTo("SETTLEMENT_CREATED");
                    assertThat(values[3]).isEqualTo("SETTLEMENT");
                    assertThat(values[4]).isEqualTo(targetId);
                    assertThat(values[5]).isEqualTo(Timestamp.from(occurredAt));
                    assertThat(values[6]).isEqualTo("corr-51");
                    assertThat(values[7]).isEqualTo("{\"itemCount\":2}");
                });
    }
}
