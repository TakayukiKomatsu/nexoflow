package com.srm.creditengine.receivable.domain;

import static com.srm.creditengine.receivable.domain.ReceivableRegistration.RegisterCommand;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ReceivableRegistrationTest {

    @Test
    void rejectsDueDateThatIsNotAfterIssueDate() {
        var command = new RegisterCommand(null, UUID.randomUUID(), "MERCANTILE_INVOICE",
                new BigDecimal("10.0000"), "BRL", LocalDate.of(2030, 1, 2),
                LocalDate.of(2030, 1, 2), "operator@srm.local");
        assertThatThrownBy(() -> ReceivableRegistration.validate(command))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsDueDateBeforeIssueDate() {
        var command = new RegisterCommand(null, UUID.randomUUID(), "MERCANTILE_INVOICE",
                new BigDecimal("10.0000"), "BRL", LocalDate.of(2030, 2, 1),
                LocalDate.of(2030, 1, 1), "operator@srm.local");
        assertThatThrownBy(() -> ReceivableRegistration.validate(command))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsZeroFaceAmount() {
        var command = new RegisterCommand(null, UUID.randomUUID(), "MERCANTILE_INVOICE",
                BigDecimal.ZERO, "BRL", LocalDate.of(2030, 1, 1),
                LocalDate.of(2030, 2, 1), "operator@srm.local");
        assertThatThrownBy(() -> ReceivableRegistration.validate(command))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNegativeFaceAmount() {
        var command = new RegisterCommand(null, UUID.randomUUID(), "MERCANTILE_INVOICE",
                new BigDecimal("-1.00"), "BRL", LocalDate.of(2030, 1, 1),
                LocalDate.of(2030, 2, 1), "operator@srm.local");
        assertThatThrownBy(() -> ReceivableRegistration.validate(command))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsFaceAmountWithMoreThanFourDecimalPlaces() {
        var command = new RegisterCommand(null, UUID.randomUUID(), "MERCANTILE_INVOICE",
                new BigDecimal("10.00001"), "BRL", LocalDate.of(2030, 1, 1),
                LocalDate.of(2030, 2, 1), "operator@srm.local");
        assertThatThrownBy(() -> ReceivableRegistration.validate(command))
                .isInstanceOf(IllegalArgumentException.class);
    }


    @Test
    void rejectsMissingAmountIssueDateAndDueDate() {
        var assignor = UUID.randomUUID();
        assertThatThrownBy(() -> ReceivableRegistration.validate(new RegisterCommand(
                null, assignor, "MERCANTILE_INVOICE", null, "BRL", LocalDate.of(2030, 1, 1),
                LocalDate.of(2030, 2, 1), "operator@srm.local"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ReceivableRegistration.validate(new RegisterCommand(
                null, assignor, "MERCANTILE_INVOICE", BigDecimal.ONE, "BRL", null,
                LocalDate.of(2030, 2, 1), "operator@srm.local"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ReceivableRegistration.validate(new RegisterCommand(
                null, assignor, "MERCANTILE_INVOICE", BigDecimal.ONE, "BRL", LocalDate.of(2030, 1, 1),
                null, "operator@srm.local"))).isInstanceOf(IllegalArgumentException.class);
    }
    @Test
    void acceptsValidCommand() {
        var command = new RegisterCommand(null, UUID.randomUUID(), "MERCANTILE_INVOICE",
                new BigDecimal("1000.0000"), "BRL", LocalDate.of(2030, 1, 1),
                LocalDate.of(2030, 2, 1), "operator@srm.local");
        assertThatCode(() -> ReceivableRegistration.validate(command)).doesNotThrowAnyException();
    }

    @Test
    void rejectsAReceivableMaturityBeyondTenYears() {
        var command = new RegisterCommand(
                null,
                UUID.randomUUID(),
                "MERCANTILE_INVOICE",
                new BigDecimal("1000.0000"),
                "BRL",
                LocalDate.of(2030, 1, 15),
                LocalDate.of(2040, 1, 16),
                "operator@srm.local");

        assertThatThrownBy(() -> ReceivableRegistration.validate(command))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Receivable maturity must not exceed ten years");
    }

    @ParameterizedTest
    @MethodSource("invalidActors")
    void rejectsMissingBlankAndOversizedActors(String actor) {
        var command = new RegisterCommand(
                null,
                UUID.randomUUID(),
                "MERCANTILE_INVOICE",
                new BigDecimal("1000.0000"),
                "BRL",
                LocalDate.of(2030, 1, 15),
                LocalDate.of(2030, 2, 15),
                actor);

        assertThatThrownBy(() -> ReceivableRegistration.validate(command))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Receivable actor must not exceed 320 characters");
    }

    static Stream<Arguments> invalidActors() {
        return Stream.of(
                Arguments.of((String) null),
                Arguments.of(" "),
                Arguments.of("x".repeat(321)));
    }
}
