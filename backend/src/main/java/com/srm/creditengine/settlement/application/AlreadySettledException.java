package com.srm.creditengine.settlement.application;

public class AlreadySettledException extends RuntimeException {
    public AlreadySettledException() { super("One or more receivables have already been settled."); }
}
