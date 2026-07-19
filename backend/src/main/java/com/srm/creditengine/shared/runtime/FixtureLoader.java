package com.srm.creditengine.shared.runtime;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;

/** Loads only review fixtures; production never creates or resets data through this component. */
@Configuration
class FixtureLoader {
    @Bean
    @Profile("e2e-fixtures")
    ApplicationRunner e2eFixtures(JdbcTemplate jdbc, Clock clock) {
        return args -> {
            Instant instant = clock.instant();
            jdbc.update(
                    "insert into assignors(id,legal_name,normalized_tax_id,active,created_at,created_by) "
                            + "values (?, 'E2E Test Assignor SA', 'E2E-ASSIGNOR-00000201', true, ?, 'e2e-fixtures') "
                            + "on conflict (id) do update set legal_name=excluded.legal_name, "
                            + "normalized_tax_id=excluded.normalized_tax_id, active=excluded.active, "
                            + "created_at=excluded.created_at, created_by=excluded.created_by",
                    UUID.fromString("00000000-0000-0000-0000-000000000201"),
                    Timestamp.from(instant));
            jdbc.update(
                            "insert into exchange_rates(id,base_currency_code,quote_currency_code,rate,source,observed_at,created_at,created_by) "
                            + "values (?, 'USD', 'BRL', 5.2000000000, 'baseline-v1', ?, ?, 'e2e-fixtures') "
                            + "on conflict (id) do nothing",
                    UUID.fromString("00000000-0000-0000-0000-000000000202"),
                    Timestamp.from(instant),
                    Timestamp.from(instant));
            fixtureRunner(jdbc, clock, "baseline-v1", List.of(
                            new Fixture("e2e-clock", "2030-01-15T12:00:00Z"),
                            new Fixture("e2e-usd-brl-rate", "USD/BRL=5.2000000000"),
                            new Fixture("e2e-assignor-id", "00000000-0000-0000-0000-000000000201")))
                    .run(args);
        };
    }

    @Bean
    @Profile("dev-fixtures")
    ApplicationRunner devFixtures(JdbcTemplate jdbc, Clock clock) {
        return fixtureRunner(jdbc, clock, "dev-v1", List.of(
                new Fixture("dev-reference-data", "relative-and-non-expiring"),
                new Fixture("dev-usd-brl-rate", "USD/BRL=5.2000000000")));
    }

    private ApplicationRunner fixtureRunner(JdbcTemplate jdbc, Clock clock, String fixtureSet, List<Fixture> fixtures) {
        return (ApplicationArguments args) -> fixtures.forEach(fixture -> jdbc.update(
                "insert into runtime_fixture_records(fixture_id, fixture_set, fixture_value, loaded_at) "
                        + "values (?, ?, ?, ?) on conflict (fixture_id) do nothing",
                fixture.id(), fixtureSet, fixture.value(), Timestamp.from(clock.instant())));
    }

    private record Fixture(String id, String value) {}
}
