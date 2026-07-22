# Case Brief Conformance Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Satisfy every locally controllable mandatory senior/staff requirement in `docs/README_case_dev_srm.md` with real architecture, behavior, documentation, and executable proof.

**Architecture:** Preserve public HTTP contracts while moving financial modules to `api -> application -> domain` and `application -> infrastructure` dependencies. React keeps server-authoritative calculation but separates view rendering from orchestration and catches unexpected render failures. Git evidence runs only in disposable clones; hosted delivery stays explicitly authorization-gated.

**Tech Stack:** Java 21, Spring Boot 3.5, JdbcTemplate/PostgreSQL/Flyway, JUnit 5/ArchUnit/Testcontainers/Cucumber, React 19, TypeScript 6, Vitest, Testing Library, Playwright, Bash, Make.

## Global Constraints

- BRL and USD only; HTTP financial values remain decimal strings; `BigDecimal`/PostgreSQL `NUMERIC` with `HALF_EVEN` remain authoritative.
- Keep Strategy pricing, immutable 15-minute quote snapshots, final-step FX conversion, scoped idempotency, locking order, optimistic locking, and whole-settlement reversal behavior unchanged.
- Reporting remains a permitted two-layer JDBC read model.
- Frontend performs no authoritative financial arithmetic; `frontend/scripts/validate-authoritative-pricing.mjs` must remain green.
- Do not add a global-state library, Terraform, Kubernetes, client-side calculation, compatibility alias, test-only production bypass, fabricated hosted URL, or remote mutation.
- Never move, delete, or reuse the existing annotated `v1.0.0` tag. Do not create a new tag without explicit owner approval of exact SHA and version.
- Generated timing-only changes in `docs/evidence/reporting-explain.txt` are restored after verification, never committed.

---

### Task 1: Establish verifiable financial-module layering

**Files:**
- Modify: `backend/src/test/java/com/srm/creditengine/architecture/ModuleArchitectureTest.java`
- Create: `backend/src/test/java/com/srm/creditengine/architecture/FinancialModuleLayeringTest.java`
- Modify: `backend/build.gradle`

**Interfaces:**
- Consumes: package roots `assignor`, `receivable`, `currency`, `pricing`, and `settlement`.
- Produces: an ArchUnit contract that all five modules contain non-empty `domain`, `application`, and `infrastructure` packages; domain imports neither Spring nor JDBC; application imports neither `JdbcTemplate` nor `java.sql`; API depends only on application/domain contracts; infrastructure is the only JDBC layer.

- [ ] **Step 1: Write the failing architecture contract**

```java
@Test
void financialModulesKeepDomainAndApplicationIndependentOfJdbc() {
    var classes = new ClassFileImporter().importPackages("com.srm.creditengine");
    for (String module : List.of("assignor", "receivable", "currency", "pricing", "settlement")) {
        assertThat(classes.stream().anyMatch(candidate ->
                candidate.getPackageName().startsWith("com.srm.creditengine." + module + ".domain"))).isTrue();
        noClasses().that().resideInAPackage(".." + module + ".domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework..", "org.springframework.jdbc..", "java.sql..", "javax.sql..")
                .check(classes);
        noClasses().that().resideInAPackage(".." + module + ".application..")
                .should().dependOnClassesThat().resideInAnyPackage("org.springframework.jdbc..", "java.sql..", "javax.sql..")
                .check(classes);
    }
}
```

- [ ] **Step 2: Run the architecture test to verify it fails**

Run: `./scripts/with-java21.sh ./backend/gradlew -p backend test --tests '*FinancialModuleLayeringTest'`

Expected: FAIL because current `Jdbc*Service` classes live in `application` and all five modules lack the required complete package boundary.

- [ ] **Step 3: Extend the coverage selector for extracted risk code**

Update `riskCoverageIncludes` in `backend/build.gradle` so financial `domain/**`, `application/**`, and `infrastructure/**` class files are included. Keep API exclusions. The gate must still demand 95% line, branch, and method coverage over the financial and security scope.

- [ ] **Step 4: Run the existing risk gate baseline**

Run: `make test-coverage`

Expected: PASS before the refactor; record the command output only, not generated coverage artifacts.

- [ ] **Step 5: Commit the red-contract setup only after the first refactor task turns it green**

The following tasks keep this contract and production changes in the same commits; never leave `main` with a deliberately failing architecture test.

### Task 2: Extract assignor and receivable domain/application/infrastructure layers

**Files:**
- Create: `backend/src/main/java/com/srm/creditengine/assignor/domain/Assignor.java`
- Create: `backend/src/main/java/com/srm/creditengine/assignor/domain/TaxId.java`
- Create: `backend/src/main/java/com/srm/creditengine/assignor/application/AssignorRepository.java`
- Create: `backend/src/main/java/com/srm/creditengine/assignor/application/AssignorApplicationService.java`
- Create: `backend/src/main/java/com/srm/creditengine/assignor/infrastructure/JdbcAssignorRepository.java`
- Modify: `backend/src/main/java/com/srm/creditengine/assignor/application/AssignorService.java`
- Remove: `backend/src/main/java/com/srm/creditengine/assignor/application/JdbcAssignorService.java`
- Create: `backend/src/main/java/com/srm/creditengine/receivable/domain/Receivable.java`
- Create: `backend/src/main/java/com/srm/creditengine/receivable/domain/ReceivableRegistration.java`
- Create: `backend/src/main/java/com/srm/creditengine/receivable/application/ReceivableRepository.java`
- Create: `backend/src/main/java/com/srm/creditengine/receivable/application/AssignorStatusReader.java`
- Create: `backend/src/main/java/com/srm/creditengine/receivable/application/ReceivableApplicationService.java`
- Create: `backend/src/main/java/com/srm/creditengine/receivable/infrastructure/JdbcReceivableRepository.java`
- Create: `backend/src/main/java/com/srm/creditengine/receivable/infrastructure/JdbcAssignorStatusReader.java`
- Remove: `backend/src/main/java/com/srm/creditengine/receivable/application/JdbcReceivableService.java`
- Test: `backend/src/test/java/com/srm/creditengine/assignor/domain/TaxIdTest.java`
- Test: `backend/src/test/java/com/srm/creditengine/receivable/domain/ReceivableRegistrationTest.java`

**Interfaces:**
- `TaxId.normalize(String raw): String` rejects blank normalized values.
- `ReceivableRegistration.validate(RegisterCommand command): void` rejects a non-positive amount, scale greater than four, missing dates, or a due date not after issue date.
- `AssignorRepository.save(Assignor assignor): void`, `findById(UUID id): Optional<Assignor>`, `findAll(): List<Assignor>`.
- `ReceivableRepository.save(Receivable receivable): void`, `findById(UUID id): Optional<Receivable>`, `findAll(): List<Receivable>`.
- Existing `AssignorService` and `ReceivableService` records/endpoints remain source-compatible.

- [ ] **Step 1: Write failing pure-domain tests**

```java
@Test
void normalizesTaxIdWithoutChangingAlphanumericContent() {
    assertThat(TaxId.normalize("12.345-AB")).isEqualTo("12345AB");
}

@Test
void rejectsDueDateThatIsNotAfterIssueDate() {
    var command = new RegisterCommand(null, UUID.randomUUID(), "MERCANTILE_INVOICE",
            new BigDecimal("10.0000"), "BRL", LocalDate.of(2030, 1, 2),
            LocalDate.of(2030, 1, 2), "operator@srm.local");
    assertThatThrownBy(() -> ReceivableRegistration.validate(command))
            .isInstanceOf(IllegalArgumentException.class);
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./scripts/with-java21.sh ./backend/gradlew -p backend test --tests '*TaxIdTest' --tests '*ReceivableRegistrationTest'`

Expected: FAIL because the domain types do not exist.

- [ ] **Step 3: Implement pure domain rules and application orchestration**

`AssignorApplicationService` creates `Assignor(id, legalName, TaxId.normalize(...), active, clock.instant())` and delegates only to `AssignorRepository`. `ReceivableApplicationService` calls `ReceivableRegistration.validate`, requires `AssignorStatusReader.isActive(assignorId)`, creates a `REGISTERED` domain `Receivable` at version zero, and delegates to `ReceivableRepository`. Neither application service imports JDBC or SQL types.

- [ ] **Step 4: Move all SQL into repositories**

Transfer the exact SQL, ordering, timestamps, and row mapping from the two existing `Jdbc*Service` classes to `JdbcAssignorRepository`, `JdbcReceivableRepository`, and `JdbcAssignorStatusReader`. Annotate write application methods `@Transactional`; repository classes use `@Repository`. Keep the API's service interfaces and response records unchanged.

- [ ] **Step 5: Run focused and regression tests**

Run: `./scripts/with-java21.sh ./backend/gradlew -p backend test --tests '*TaxIdTest' --tests '*ReceivableRegistrationTest' --tests '*ReceivableControllerTest' && make test-api-features`

Expected: PASS; registration, lookup, and API behavior remain unchanged.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/srm/creditengine/{assignor,receivable} backend/src/test/java/com/srm/creditengine/{assignor,receivable,architecture} backend/build.gradle
git commit -m "refactor(core): separate assignor and receivable persistence"
```

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

### Task 5: Add frontend render-failure recovery and component boundaries

**Files:**
- Create: `frontend/src/components/AppErrorBoundary.tsx`
- Create: `frontend/src/components/AppErrorBoundary.test.tsx`
- Create: `frontend/src/components/Login.tsx`
- Create: `frontend/src/components/PricingForm.tsx`
- Create: `frontend/src/components/PricingBreakdown.tsx`
- Create: `frontend/src/components/StatementLedger.tsx`
- Create: `frontend/src/hooks/useLiveSimulation.ts`
- Create: `frontend/src/hooks/useSettlementIntent.ts`
- Create: `frontend/src/hooks/useStatementFilters.ts`
- Modify: `frontend/src/main.tsx`
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/SettlementWorkspace.tsx`
- Modify: `frontend/src/App.test.tsx`
- Modify: `frontend/src/SettlementWorkspace.test.tsx`
- Modify: `frontend/src/a11y.test.tsx`

**Interfaces:**
- `AppErrorBoundary` wraps the app and renders `role="alert"`, a retry button, and no thrown error text.
- `useLiveSimulation(values, session, onExpired)` returns `{ simulation, state, feedback }`; it owns the 300ms debounce, abort, monotonic request ID, and server-only API call.
- `useSettlementIntent(session, quotes, onExpired)` returns explicit view state and handlers for selection, preview, confirmation, cancellation, and reversal; it preserves existing localStorage idempotency-key behavior.
- `StatementLedger` receives already-derived `search`, `onNavigate`, and session token props; it performs no client-side result filtering or calculation.

- [ ] **Step 1: Write the failing error-boundary test**

```tsx
function BrokenChild(): never {
  throw new Error("render defect");
}

it("renders an accessible recovery view for an unexpected child render failure", () => {
  render(<AppErrorBoundary><BrokenChild /></AppErrorBoundary>);
  expect(screen.getByRole("alert")).toHaveTextContent("Something went wrong");
  expect(screen.getByRole("button", { name: "Try again" })).toBeEnabled();
  expect(screen.queryByText("render defect")).not.toBeInTheDocument();
});
```

- [ ] **Step 2: Run it to verify it fails**

Run: `npm --prefix frontend run test -- --run src/components/AppErrorBoundary.test.tsx`

Expected: FAIL because the boundary does not exist.

- [ ] **Step 3: Implement `AppErrorBoundary` and wire it at the root**

Use a React class boundary with `static getDerivedStateFromError(): { failed: true }`, `componentDidCatch(error, info)` that emits only a fixed `console.error("SRM UI render failure")`, and a retry handler that clears `failed`. In `main.tsx`, render `<AppErrorBoundary><App /></AppErrorBoundary>` inside `StrictMode`. The fallback must use semantic `<main>`, `role="alert"`, and a `Try again` button.

- [ ] **Step 4: Extract components and hooks without contract drift**

Move `Login` to `components/Login.tsx`; move form controls and display-only pricing result markup to `PricingForm.tsx`/`PricingBreakdown.tsx`; move simulation effect and only its state to `useLiveSimulation.ts`; move ledger URL/filter/pagination rendering to `StatementLedger.tsx` with `useStatementFilters.ts`; move settlement localStorage/preview/confirm logic to `useSettlementIntent.ts`. Keep `App.tsx` responsible for session and composition; keep `SettlementWorkspace.tsx` responsible for composing its hook and view components. Preserve element labels, headings, roles, API paths, and error copy used by existing tests.

- [ ] **Step 5: Run focused behavior and accessibility tests**

Run: `npm --prefix frontend run test -- --run src/components/AppErrorBoundary.test.tsx src/App.test.tsx src/SettlementWorkspace.test.tsx src/a11y.test.tsx && npm --prefix frontend run typecheck && npm --prefix frontend run validate:authoritative-pricing`

Expected: PASS. The new fallback is covered; current simulation, stale/cancel behavior, quote/settlement intent, ledger URL state, and no-local-financial-arithmetic tests remain green.

- [ ] **Step 6: Run browser proof and commit**

Run: `make test-ui-features`

Expected: PASS: real browser login, simulation, quote, preview, settlement, replay, reversal, and paginated ledger remain intact.

```bash
git add frontend/src
git commit -m "refactor(frontend): separate workflow views and recover renders"
```

### Task 6: Add safe Git-process evidence and required README content

**Files:**
- Create: `scripts/test-rebase-evidence.sh`
- Create: `scripts/tests/test_rebase_fixture.sh`
- Create: `docs/evidence/pull-request-simulation.md`
- Modify: `scripts/test-crisis-evidence.sh`
- Modify: `Makefile`
- Modify: `.github/workflows/ci.yml`
- Modify: `README.md`
- Modify: `AI_USAGE.md`
- Modify: `docs/GIT_WORKFLOW.md`
- Modify: `docs/REQUIREMENT_TRACEABILITY.md`
- Test: `scripts/tests/test_rebase_evidence.sh`

**Interfaces:**
- `make test-rebase-evidence` runs `scripts/test-rebase-evidence.sh` and exits zero only after a disposable clone proves an autosquashed, linear history with no `fixup!` subject.
- `make test-crisis-evidence` retains its name and exits zero only after a disposable clone's branch named `main` has a harmless regression/revert pair and recovered fast gate.
- `docs/evidence/pull-request-simulation.md` has no hosted URL and labels itself as a local simulation with source/target, scope, tests, security, migrations, rollback, residual risks, and review conclusion.

- [ ] **Step 1: Write the failing rebase-proof test**

```bash
#!/usr/bin/env bash
set -euo pipefail
output="$("$(cd "$(dirname "$0")/../.." && pwd)/scripts/test-rebase-evidence.sh")"
grep -q 'REBASE-001 passed' <<<"$output"
```

- [ ] **Step 2: Run it to verify it fails**

Run: `bash scripts/tests/test_rebase_evidence.sh`

Expected: FAIL because `scripts/test-rebase-evidence.sh` does not exist.

- [ ] **Step 3: Implement the disposable autosquash proof**

The script creates a temporary local clone, configures a simulation author, commits a harmless fixture baseline and a `fixup!` change, invokes `GIT_SEQUENCE_EDITOR=true git rebase -i --autosquash HEAD~2`, then asserts one commit remains, no subject begins `fixup!`, and the fixture passes. It deletes the temporary clone by `trap`; it never changes the working repository, its branches, tags, remotes, or configuration.

- [ ] **Step 4: Make crisis proof use only an isolated clone's `main`**

Replace `checkout -b simulation/crisis-revert` with `checkout -B main` in the temporary clone and update the emitted evidence to `branch=main (disposable clone)`. Preserve the harmless fixture-only mutation, expected failure, `git revert`, recovery gate, two-commit assertion, and cleanup. Update docs to state this is a safe simulation of a defect reaching a clone's `main`, not a defect committed to this repository's `main`.

- [ ] **Step 5: Add README-required staff content**

Add two root-README sections:

```markdown
## Delivery workflow
This repository uses GitHub Flow: short-lived feature branches are rebased while unpublished, reviewed, then merged to one releasable `main`. This fits a small delivery team because it minimizes coordination overhead while keeping integration continuously releasable.

## Proposed 1M transactions/minute evolution
This implementation is a modular monolith, not a throughput claim. At measured scale, preserve idempotency at ingress; publish an outbox to a partitioned event stream by assignor/time; materialize statement read models; cache versioned reference data; evolve PostgreSQL partitioning toward sharding; keep settlement writes strongly consistent within a partition; and use eventual consistency only for analytics. Introduce backpressure, DLQ/replay, SLOs, capacity models, disaster recovery, and ownership before extracting services.
```

Link the detailed Git workflow and scale rationale. Mark the second section **Proposed**.

- [ ] **Step 6: Add truthful local PR/release documentation**

Write the local PR simulation using the real current branch/commit and exact verification output. Correct README, AI usage, and traceability wording: `v1.0.0` is an existing stale local annotated tag; remote PR/publication/release and a final new version tag are not performed without authorization. Do not claim CI ran remotely when no remote exists.

- [ ] **Step 7: Wire and execute local evidence checks**

Add `test-rebase-evidence` to `Makefile`, invoke it in `verify-fast` and CI, and run:

Run: `make test-rebase-evidence && make test-crisis-evidence && make validate-docs && make validate-traceability`

Expected: PASS; output identifies disposable-only operations and docs resolve every new command/path.

- [ ] **Step 8: Commit**

```bash
git add scripts Makefile .github/workflows/ci.yml README.md AI_USAGE.md docs/GIT_WORKFLOW.md docs/REQUIREMENT_TRACEABILITY.md docs/evidence/pull-request-simulation.md
git commit -m "docs: prove local senior staff delivery workflow"
```

### Task 7: Complete acceptance verification and independent review

**Files:**
- Modify only if verification exposes a concrete defect: the owning task's files.
- Do not commit generated reports: `backend/build/**`, `frontend/coverage/**`, `frontend/playwright-report/**`, `build/security/**`, or timing-only `docs/evidence/reporting-explain.txt` changes.

**Interfaces:**
- Consumes all prior tasks and Make targets.
- Produces fresh complete local proof and a final independent review verdict.

- [ ] **Step 1: Run focused gates in dependency order**

Run:

```bash
./scripts/with-java21.sh ./backend/gradlew -p backend test --tests '*FinancialModuleLayeringTest'
npm --prefix frontend run test -- --run
npm --prefix frontend run typecheck
make test-rebase-evidence
make test-crisis-evidence
make validate-docs
make validate-traceability
```

Expected: all commands exit zero.

- [ ] **Step 2: Run complete local acceptance**

Run: `make release-check`

Expected: exit zero, including unit/integration/Cucumber, coverage, Compose/readiness, Playwright, representative SQL, security/SBOM/licenses, documentation/traceability, and safe crisis/rebase evidence.

- [ ] **Step 3: Restore volatile generated evidence and inspect scope**

Run:

```bash
git restore -- docs/evidence/reporting-explain.txt
git status --short
```

Expected: only intentionally preserved user/IDE artifacts may remain untracked; no generated report or unrelated source change is staged.

- [ ] **Step 4: Obtain independent code review**

Ask a reviewer to compare the final diff against `docs/README_case_dev_srm.md` and this plan. Address every Critical/Important evidence-backed finding, then rerun the owning focused command and `make release-check` if production behavior changed.

- [ ] **Step 5: Commit only corrective changes from review**

Use a scoped Conventional Commit. Do not create, move, delete, or push Git tags; do not configure or mutate a remote.

## Spec Coverage Review

- Financial three-layer separation: Tasks 1–4.
- Frontend presentation/state separation and unexpected-error recovery: Task 5.
- README GitHub Flow rationale and 1M TPM design: Task 6.
- Local PR, interactive-rebase, and safe-main crisis proof: Task 6.
- Stale tag documentation and remote-delivery truthfulness: Task 6.
- Fresh local validation and independent review: Task 7.
- Optional Terraform/Kubernetes and authorization-gated remote publication/release: intentionally excluded by the approved specification.
