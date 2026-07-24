package com.srm.creditengine.shared.domain;

/** Signals an absent caller-addressable domain resource without carrying identifiers. */
public final class DomainResourceNotFoundException extends RuntimeException {
    public DomainResourceNotFoundException() {
        super("The requested domain resource was not found.");
    }
}
