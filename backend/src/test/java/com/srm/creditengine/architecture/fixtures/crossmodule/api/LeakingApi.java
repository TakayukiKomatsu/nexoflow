package com.srm.creditengine.architecture.fixtures.crossmodule.api;

import com.srm.creditengine.settlement.infrastructure.JdbcSettlementRepository;

/** Deliberate test fixture proving cross-module infrastructure bypasses are rejected. */
public final class LeakingApi {
    private JdbcSettlementRepository adapter;

    public JdbcSettlementRepository adapter() {
        return adapter;
    }
}
