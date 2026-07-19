package com.srm.creditengine.cucumber;

import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import java.time.Clock;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;

/** One cached Spring Boot context for the full Cucumber suite.
 *  The PostgreSQL container is Spring-managed via @ServiceConnection — no @Container or @Testcontainers. */
@CucumberContextConfiguration
@ActiveProfiles("test")
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "srm.jwt-secret=srm-test-secret-do-not-use-32-bytes-minimum",
            "srm.clock.fixed-instant=2030-01-15T12:00:00Z",
            "srm.fx-provider.base-url=http://127.0.0.1:18090",
            "management.endpoints.web.exposure.include=health,info,prometheus",
            "management.prometheus.metrics.export.enabled=true",
            "management.observations.enable.http.client.requests=false"
        })
@Import({PostgresContainerConfiguration.class, CucumberSpringConfiguration.AcceptanceInfrastructure.class})
public class CucumberSpringConfiguration {
    private static final AcceptanceFxProviderStub FX_PROVIDER = AcceptanceFxProviderStub.start(18090);
    private static final AcceptanceClock CLOCK = new AcceptanceClock();

    @TestConfiguration(proxyBeanMethods = false)
    static class AcceptanceInfrastructure {
        @Bean(destroyMethod = "close")
        AcceptanceFxProviderStub acceptanceFxProviderStub() {
            return FX_PROVIDER;
        }

        @Bean
        @Primary
        Clock acceptanceClock() {
            return CLOCK;
        }

        @Bean(destroyMethod = "close")
        AcceptanceLogCapture acceptanceLogCapture() {
            return new AcceptanceLogCapture();
        }
    }
}
