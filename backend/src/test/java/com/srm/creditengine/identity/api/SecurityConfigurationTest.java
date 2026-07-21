package com.srm.creditengine.identity.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class SecurityConfigurationTest {
    @Test
    void configuresSystemAndFixedApplicationClocksAndRejectsShortJwtSecrets() {
        SecurityConfiguration configuration = new SecurityConfiguration();

        assertThat(configuration.applicationClock(" ").getZone().getId()).isEqualTo("Z");
        assertThat(configuration.applicationClock("2030-01-15T12:00:00Z").instant())
                .isEqualTo(Instant.parse("2030-01-15T12:00:00Z"));
        assertThatThrownBy(() -> configuration.jwtKey("too-short"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("SRM_JWT_SECRET must contain at least 32 bytes");
    }
}
