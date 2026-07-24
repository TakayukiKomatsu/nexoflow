### Task 4: Extract settlement domain/application/infrastructure layers

**Files:**
- Create: `backend/src/main/java/com/srm/creditengine/settlement/domain/SettlementDraft.java`
- Create: `backend/src/main/java/com/srm/creditengine/settlement/domain/SettlementPolicy.java`
- Create: `backend/src/main/java/com/srm/creditengine/settlement/application/SettlementRepository.java`
- Create: `backend/src/main/java/com/srm/creditengine/settlement/application/IdempotencyRepository.java`
- Create: `backend/src/main/java/com/srm/creditengine/settlement/application/AuditEventRecorder.java`
- Create: `backend/src/main/java/com/srm/creditengine/settlement/application/SettlementApplicationService.java`
- Create: `backend/src/main/java/com/srm/creditengine/settlement/infrastructure/JdbcSettlementRepository.java`
- Create: `backend/src/main/java/com/srm/creditengine/settlement/infrastructure/JdbcIdempotencyRepository.java`
- Create: `backend/src/main/java/com/srm/creditengine/settlement/infrastructure/JdbcAuditEventRecorder.java`
- Remove: `backend/src/main/java/com/srm/creditengine/settlement/application/JdbcSettlementService.java`
- Test: `backend/src/test/java/com/srm/creditengine/settlement/domain/SettlementPolicyTest.java`
- Test: `backend/src/test/java/com/srm/creditengine/architecture/FinancialModuleLayeringTest.java`

**Interfaces:**
- `SettlementRepository.lockQuotes(List<UUID> orderedIds): List<LockedQuote>`, `saveCompleted(SettlementDraft draft): void`, `consumeQuoteAndReceivable(LockedQuote quote): void`, `findResult(UUID id): Optional<SettlementService.Result>`, `lockSettlement(UUID id): LockedSettlement`, `reverse(LockedSettlement settlement, String reason, Instant at, String actor): SettlementService.Reversal`.
- `IdempotencyRepository.claim(String actor, String operation, String key, String hash, Instant at): IdempotencyRecord`, `completeSettlement(UUID recordId, UUID settlementId, Instant at): void`, and equivalent reversal completion.
- `SettlementPolicy.requireOrderedUnique`, `validateQuotes`, `previewOf`, and `requestHash` have no Spring/JDBC dependencies.

- [ ] **Step 1: Write failing domain tests**

```java
@Test
void rejectsDuplicateQuoteIdsBeforePersistence() {
    var quoteId = UUID.randomUUID();
    assertThatThrownBy(() -> SettlementPolicy.requireOrderedUnique(List.of(quoteId, quoteId)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("ordered and unique");
}

@Test
void previewRequiresOneAssignorAndSettlementCurrency() {
    assertThatThrownBy(() -> SettlementPolicy.previewOf(List.of(brlQuote, usdQuote), NOW))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("one assignor and settlement currency");
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./scripts/with-java21.sh ./backend/gradlew -p backend test --tests '*SettlementPolicyTest'`

Expected: FAIL because the policy type does not exist.

- [ ] **Step 3: Move pure rules and preserve transaction scope**

Move request hashing, ordered/unique validation, quote state validation, batch currency/assignor invariant, preview total, and reversal-input validation into `SettlementPolicy`. `SettlementApplicationService` remains the only Spring `SettlementService` implementation and keeps `@Transactional` on settle/reverse. It coordinates repositories without importing `JdbcTemplate` or `java.sql`.

- [ ] **Step 4: Move locking SQL and writes to JDBC adapters**

Transfer the exact `FOR UPDATE` quote/idempotency/settlement reads, ordered query mapping, `CONSUMED` and versioned `REGISTERED -> SETTLED` updates, reversal writes, audit records, and result mapping from `JdbcSettlementService` into the three JDBC adapters. Preserve lock order, unique actor/operation/key behavior, status transitions, correlation ID metadata, and rollback semantics exactly.

- [ ] **Step 5: Run settlement correctness checks**

Run: `./scripts/with-java21.sh ./backend/gradlew -p backend test --tests '*SettlementPolicyTest' && make test-runtime && make test-api-features`

Expected: PASS: real PostgreSQL race, idempotent replay/conflict, injected rollback, preview, settlement, and reversal scenarios remain unchanged.

- [ ] **Step 6: Run the architecture and coverage gates**

Run: `./scripts/with-java21.sh ./backend/gradlew -p backend test --tests '*ModuleArchitectureTest' --tests '*FinancialModuleLayeringTest' && make test-coverage`

Expected: PASS: all five financial modules satisfy the three-layer contract at 95% risk coverage.

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/srm/creditengine/settlement backend/src/test/java/com/srm/creditengine/{settlement,architecture} backend/build.gradle
git commit -m "refactor(settlement): separate domain and persistence"
```

