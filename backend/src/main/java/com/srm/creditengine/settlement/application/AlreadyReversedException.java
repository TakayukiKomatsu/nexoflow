package com.srm.creditengine.settlement.application;

public class AlreadyReversedException extends RuntimeException {
    public AlreadyReversedException() { super("Settlement has already been reversed"); }
}
