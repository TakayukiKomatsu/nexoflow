package com.srm.creditengine.assignor.domain;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class AssignorTest {
    private static final UUID ID = UUID.fromString("00000000-0000-0000-0000-000000000101");
    private static final Instant CREATED_AT = Instant.parse("2030-01-15T12:00:00Z");

    @ParameterizedTest
    @MethodSource("invalidLegalNames")
    void rejectsMissingBlankAndOversizedLegalNames(String legalName) {
        assertThatThrownBy(() -> new Assignor(
                        ID, legalName, "123456789", true, CREATED_AT, "operator@srm.local"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Assignor legal name must not exceed 200 characters");
    }

    static Stream<Arguments> invalidLegalNames() {
        return Stream.of(
                Arguments.of((String) null),
                Arguments.of(" "),
                Arguments.of("x".repeat(201)));
    }

    @ParameterizedTest
    @MethodSource("invalidActors")
    void rejectsMissingBlankAndOversizedActors(String actor) {
        assertThatThrownBy(() -> new Assignor(
                        ID, "Validated Assignor", "123456789", true, CREATED_AT, actor))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Assignor actor must not exceed 320 characters");
    }

    static Stream<Arguments> invalidActors() {
        return Stream.of(
                Arguments.of((String) null),
                Arguments.of(" "),
                Arguments.of("x".repeat(321)));
    }
}
