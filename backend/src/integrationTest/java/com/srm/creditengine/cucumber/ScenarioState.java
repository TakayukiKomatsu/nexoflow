package com.srm.creditengine.cucumber;

import io.cucumber.spring.ScenarioScope;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.ScopedProxyMode;

/** Per-scenario mutable state. A new instance is created for each scenario by @ScenarioScope.
 *  Never use static fields; never share state across scenarios via this class. */
@Component
@ScenarioScope(proxyMode = ScopedProxyMode.NO)
class ScenarioState {

    // Auth tokens
    String operatorToken;
    String adminToken;
    String activeToken;

    // Last HTTP response
    int lastStatus;
    String lastBody;
    Map<String, List<String>> lastHeaders = new HashMap<>();

    // Domain IDs created during the scenario
    UUID assignorId = UUID.fromString("00000000-0000-0000-0000-000000000201");
    UUID lastReceivableId;
    UUID secondReceivableId;
    UUID lastQuoteId;
    UUID secondQuoteId;
    UUID lastSettlementId;
    UUID previousSettlementId;
    UUID lastReversalId;
    UUID previousReversalId;
    String currentIdempotencyKey;
    List<Integer> concurrentStatuses = new ArrayList<>();
    List<String> concurrentBodies = new ArrayList<>();
    List<String> concurrentReplayHeaders = new ArrayList<>();

    // Scoped counts for rollback assertions
    int beforeSettlementCount;
    int beforeItemCount;
    int beforeIdempotencyCount;
    int beforeAuditCount;
    int pricingQuoteCount;

    // Pagination / report state
    List<String> statementEntryIds = new ArrayList<>();
    int lastPageSize;
    boolean lastHasNext;
    List<String> firstPageEntryIds = new ArrayList<>();
    List<String> secondPageEntryIds = new ArrayList<>();
}
