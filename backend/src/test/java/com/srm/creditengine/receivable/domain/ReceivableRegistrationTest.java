package com.srm.creditengine.receivable.domain;

import static com.srm.creditengine.receivable.domain.ReceivableRegistration.RegisterCommand;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

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
    void acceptsValidCommand() {
        var command = new RegisterCommand(null, UUID.randomUUID(), "MERCANTILE_INVOICE",
                new BigDecimal("1000.0000"), "BRL", LocalDate.of(2030, 1, 1),
                LocalDate.of(2030, 2, 1), "operator@srm.local");
        assertThatCode(() -> ReceivableRegistration.validate(command)).doesNotThrowAnyException();
    }
}
