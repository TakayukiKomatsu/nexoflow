package com.srm.creditengine.settlement.domain;

public class PricingQuoteExpiredException extends RuntimeException {
    public PricingQuoteExpiredException() {
        super("A pricing quote expired. Create a fresh quote and preview.");
    }
}
