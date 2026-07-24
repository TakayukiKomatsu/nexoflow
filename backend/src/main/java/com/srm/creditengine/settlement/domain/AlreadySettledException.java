package com.srm.creditengine.settlement.domain;

public class AlreadySettledException extends RuntimeException {
    private final String settlementCurrency;

    public AlreadySettledException() {
        this(null);
    }

    public AlreadySettledException(String settlementCurrency) {
        super("One or more receivables have already been settled.");
        this.settlementCurrency = settlementCurrency;
    }

    public String settlementCurrency() {
        return settlementCurrency;
    }
}
