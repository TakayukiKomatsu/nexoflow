package com.srm.creditengine.assignor.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.srm.creditengine.assignor.domain.Assignor;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AssignorApplicationServiceTest {
    private static final Instant NOW = Instant.parse("2030-01-15T12:00:00Z");

    @Test
    void createsAndRetrievesAnAssignorWithAServerGeneratedIdentity() {
        var repository = new InMemoryAssignorRepository();
        AssignorService service =
                new AssignorApplicationService(repository, Clock.fixed(NOW, ZoneOffset.UTC));

        AssignorService.Assignor created = service.create(new AssignorService.CreateCommand(
                null,
                "Generated Identity Assignor",
                "12.345-AB",
                true,
                "operator@srm.local"));

        assertThat(created.id()).isNotNull();
        assertThat(created.taxId()).isEqualTo("12345AB");
        assertThat(created.createdAt()).isEqualTo(NOW);
        assertThat(service.get(created.id())).isEqualTo(created);
    }

    private static final class InMemoryAssignorRepository implements AssignorRepository {
        private final Map<UUID, Assignor> assignors = new LinkedHashMap<>();

        @Override
        public void save(Assignor assignor) {
            assignors.put(assignor.id(), assignor);
        }

        @Override
        public Optional<Assignor> findById(UUID id) {
            return Optional.ofNullable(assignors.get(id));
        }

        @Override
        public List<Assignor> findAll() {
            return List.copyOf(assignors.values());
        }
    }
}
