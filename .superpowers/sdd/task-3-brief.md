### Task 3: Extract currency and pricing persistence adapters

**Files:**
- Create: `backend/src/main/java/com/srm/creditengine/currency/domain/FxObservation.java`
- Create: `backend/src/main/java/com/srm/creditengine/currency/domain/FxConversionPolicy.java`
- Create: `backend/src/main/java/com/srm/creditengine/currency/application/ExchangeRateRepository.java`
- Create: `backend/src/main/java/com/srm/creditengine/currency/application/ReferenceRateRepository.java`
- Create: `backend/src/main/java/com/srm/creditengine/currency/application/CurrencyApplicationService.java`
- Create: `backend/src/main/java/com/srm/creditengine/currency/infrastructure/JdbcExchangeRateRepository.java`
- Create: `backend/src/main/java/com/srm/creditengine/currency/infrastructure/JdbcReferenceRateRepository.java`
- Remove: `backend/src/main/java/com/srm/creditengine/currency/application/JdbcCurrencyService.java`
- Move/replace: `backend/src/main/java/com/srm/creditengine/currency/application/JdbcReferenceRateService.java`
- Create: `backend/src/main/java/com/srm/creditengine/pricing/domain/PricingQuoteSnapshot.java`
- Create: `backend/src/main/java/com/srm/creditengine/pricing/application/PricingQuoteRepository.java`
- Create: `backend/src/main/java/com/srm/creditengine/pricing/application/ReceivableQuoteReader.java`
- Create: `backend/src/main/java/com/srm/creditengine/pricing/infrastructure/JdbcPricingQuoteRepository.java`
- Create: `backend/src/main/java/com/srm/creditengine/pricing/infrastructure/JdbcReceivableQuoteReader.java`
- Modify: `backend/src/main/java/com/srm/creditengine/pricing/application/AuthoritativePricingService.java`
- Test: `backend/src/test/java/com/srm/creditengine/currency/domain/FxConversionPolicyTest.java`
- Test: `backend/src/test/java/com/srm/creditengine/pricing/domain/PricingQuoteSnapshotTest.java`

**Interfaces:**
- `ExchangeRateRepository.record(FxObservation observation, String actor, Instant createdAt): void` and `latest(String base, String quote, Instant at): Optional<FxObservation>`.
- `ReferenceRateRepository.baseRates(String currency, Instant at): List<ReferenceRateService.BaseRate>` and `productSpreads(String productType, Instant at): List<ReferenceRateService.ProductSpread>`.
- `PricingQuoteRepository.save(PricingQuoteSnapshot snapshot, String actor): void`, `findById(UUID id): Optional<PricingQuoteSnapshot>`.
- `ReceivableQuoteReader.lockRegistered(UUID id): ReceivableQuoteReader.LockedReceivable`; its nested `LockedReceivable` record carries `id`, `productType`, `faceAmount`, `faceCurrency`, `dueDate`, and current `status`, and the adapter preserves `FOR UPDATE` semantics.

- [ ] **Step 1: Write failing policy/snapshot tests**

```java
@Test
void selectsFreshDirectRateBeforeFreshInverseRate() {
    var result = FxConversionPolicy.resolve(directAt("0.20"), inverseAt("5.10"),
            new BigDecimal("100.0000"), NOW);
    assertThat(result.settlementAmount()).isEqualByComparingTo("20.00");
}

@Test
void preservesEveryFinancialDecisionFieldInQuoteSnapshot() {
    assertThat(snapshot.toQuote().breakdown().settlementAmount())
            .isEqualByComparingTo("966.18");
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./scripts/with-java21.sh ./backend/gradlew -p backend test --tests '*FxConversionPolicyTest' --tests '*PricingQuoteSnapshotTest'`

Expected: FAIL because the domain policy and snapshot types do not exist.

- [ ] **Step 3: Move currency financial rules to domain and JDBC to adapters**

Move direct/inverse/identity selection, 24-hour freshness, and `HALF_EVEN` settlement rounding from `JdbcCurrencyService` into `FxConversionPolicy`; retain `SupportedCurrency.require` at the domain boundary. Move `exchange_rates` and reference-rate SQL/row mapping into the two JDBC repositories. `CurrencyApplicationService` becomes the Spring `CurrencyService` implementation and coordinates ports, clock, and transactional write.

- [ ] **Step 4: Move quote persistence to the pricing adapter**

`AuthoritativePricingService` retains simulation, strategy selection, term calculation, and telemetry, but calls `ReceivableQuoteReader` for locked state and `PricingQuoteRepository` for quote/audit persistence and reload. Map stored quote values to a domain `PricingQuoteSnapshot`; preserve `ACTIVE`/`EXPIRED` calculation and all response field scales.

- [ ] **Step 5: Run targeted and end-to-end pricing checks**

Run: `./scripts/with-java21.sh ./backend/gradlew -p backend test --tests '*PricingExactVectorTest' --tests '*AuthoritativePricingServiceTest' --tests '*FxConversionPolicyTest' --tests '*PricingQuoteSnapshotTest' && make test-api-features`

Expected: PASS, including exact vectors, stale/missing FX errors, snapshot lifecycle, and wire contract.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/srm/creditengine/{currency,pricing} backend/src/test/java/com/srm/creditengine/{currency,pricing,architecture} backend/build.gradle
git commit -m "refactor(core): isolate currency and pricing adapters"
```

