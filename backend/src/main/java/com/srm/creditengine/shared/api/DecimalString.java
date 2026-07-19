package com.srm.creditengine.shared.api;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;

public record DecimalString(BigDecimal value) {
    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static DecimalString from(JsonNode node) {
        if (node == null || !node.isTextual()) {
            throw new IllegalArgumentException("Financial values must be decimal strings");
        }
        try {
            return new DecimalString(new BigDecimal(node.textValue()));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Financial values must be valid decimal strings");
        }
    }

    @JsonValue
    public String json() {
        return value.toPlainString();
    }
}
