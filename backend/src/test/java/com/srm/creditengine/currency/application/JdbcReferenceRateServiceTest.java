package com.srm.creditengine.currency.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import com.srm.creditengine.currency.infrastructure.JdbcReferenceRateService;

import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class JdbcReferenceRateServiceTest {
    @Test
    void rejectsNullAndNonPositiveReferenceRatesBeforePersisting() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        JdbcReferenceRateService service = new JdbcReferenceRateService(jdbc);
        Instant now = Instant.parse("2030-01-15T12:00:00Z");

        assertThatThrownBy(() -> service.recordBaseRate("BRL", null, now))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A reference rate must be positive");
        assertThatThrownBy(() -> service.recordProductSpread("MERCANTILE_INVOICE", BigDecimal.ZERO, now))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A reference rate must be positive");
        verifyNoInteractions(jdbc);
    }
}
