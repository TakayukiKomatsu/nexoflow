package com.srm.creditengine.receivable.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.srm.creditengine.receivable.domain.Receivable;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReceivableApplicationServiceTest {
    private static final UUID ASSIGNOR_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000201");
    private static final Instant NOW = Instant.parse("2030-01-15T12:00:00Z");

    @Test
    void rejectsAMissingProductTypeWithoutRegisteringAReceivable() {
        var repository = new InMemoryReceivableRepository();
        ReceivableService service = service(repository, true);

        assertThatThrownBy(() -> service.register(command(UUID.randomUUID(), null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unsupported product type");
        assertThat(service.list()).isEmpty();
    }

    @Test
    void rejectsAnInactiveAssignorWithoutRegisteringAReceivable() {
        var repository = new InMemoryReceivableRepository();
        ReceivableService service = service(repository, false);

        assertThatThrownBy(() -> service.register(
                        command(UUID.randomUUID(), "MERCANTILE_INVOICE")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Receivable requires an active assignor");
        assertThat(service.list()).isEmpty();
    }

    @Test
    void registersAndRetrievesAReceivableWithAServerGeneratedIdentity() {
        var repository = new InMemoryReceivableRepository();
        ReceivableService service = service(repository, true);

        ReceivableService.Receivable created =
                service.register(command(null, "MERCANTILE_INVOICE"));

        assertThat(created.id()).isNotNull();
        assertThat(created.status()).isEqualTo("REGISTERED");
        assertThat(created.version()).isZero();
        assertThat(service.get(created.id())).isEqualTo(created);
    }

    private static ReceivableService service(
            InMemoryReceivableRepository repository, boolean assignorActive) {
        return new ReceivableApplicationService(
                repository,
                ignoredAssignorId -> assignorActive,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static ReceivableService.RegisterCommand command(UUID id, String productType) {
        return new ReceivableService.RegisterCommand(
                id,
                ASSIGNOR_ID,
                productType,
                new BigDecimal("1000.0000"),
                "BRL",
                LocalDate.of(2030, 1, 15),
                LocalDate.of(2030, 2, 15),
                "operator@srm.local");
    }

    private static final class InMemoryReceivableRepository implements ReceivableRepository {
        private final Map<UUID, Receivable> receivables = new LinkedHashMap<>();

        @Override
        public void save(Receivable receivable) {
            receivables.put(receivable.id(), receivable);
        }

        @Override
        public Optional<Receivable> findById(UUID id) {
            return Optional.ofNullable(receivables.get(id));
        }

        @Override
        public List<Receivable> findAll() {
            return List.copyOf(receivables.values());
        }
    }
}
