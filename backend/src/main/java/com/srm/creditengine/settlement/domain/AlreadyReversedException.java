package com.srm.creditengine.settlement.domain;

public class AlreadyReversedException extends RuntimeException {
    public AlreadyReversedException() {
        super("Settlement has already been reversed");
    }
}
