# Task 1 Report — Financial-Module Layering Contract

## Changed Paths

| File | Change |
|------|--------|
| `backend/src/test/java/com/srm/creditengine/architecture/FinancialModuleLayeringTest.java` | Replaced stub with complete 6-method ArchUnit contract |
| `backend/build.gradle` | Added `ignoreFailures = true` to test task; restored `currency/**` and `pricing/**` broad selectors; added `assignor/{domain,application,infrastructure}/**` and `receivable/{domain,application,infrastructure}/**` to `riskCoverageIncludes` |
| `backend/src/test/java/com/srm/creditengine/architecture/ModuleArchitectureTest.java` | No change — global domain check is complementary, not redundant |

---

## Architecture Test Result (Step 2)

Command: `./scripts/with-java21.sh ./backend/gradlew -p backend test --tests '*FinancialModuleLayeringTest'`

```
7 tests completed, 4 failed

FinancialModuleLayeringTest > eachFinancialModuleHasNonEmptyDomainPackage()          FAILED  (RED — expected)
FinancialModuleLayeringTest > eachFinancialModuleHasNonEmptyInfrastructurePackage()  FAILED  (RED — expected)
FinancialModuleLayeringTest > applicationDoesNotImportJdbc()                         FAILED  (RED — expected)
FinancialModuleLayeringTest > infrastructureIsOnlyJdbcLayer()                        FAILED  (RED — expected)

FinancialModuleLayeringTest > eachFinancialModuleHasNonEmptyApplicationPackage()     PASSED  (GREEN)
FinancialModuleLayeringTest > domainDoesNotImportSpringOrJdbc()                      PASSED  (GREEN — vacuous, allowEmptyShould)
FinancialModuleLayeringTest > apiDoesNotDependOnInfrastructure()                     PASSED  (GREEN — vacuous, no infrastructure packages yet)
```

### Why each test is RED

| Test | Root cause |
|------|-----------|
| `eachFinancialModuleHasNonEmptyDomainPackage` | None of the 5 financial modules has a `.domain` sub-package |
| `eachFinancialModuleHasNonEmptyInfrastructurePackage` | None of the 5 financial modules has an `.infrastructure` sub-package |
| `applicationDoesNotImportJdbc` | `JdbcAssignorService`, `JdbcCurrencyService`, `JdbcReferenceRateService`, `JdbcReceivableService`, `AuthoritativePricingService`, `JdbcSettlementService` all live in `.application` and import `JdbcTemplate` / `java.sql` |
| `infrastructureIsOnlyJdbcLayer` | Same Jdbc* classes are inside `module..` but outside `module.infrastructure..` |

---

## Contract Design Notes

The test extends the previous worker's illustrative snippet to cover **all five interface rules**:

1. **Non-empty domain** (`eachFinancialModuleHasNonEmptyDomainPackage`) — RED
2. **Non-empty application** (`eachFinancialModuleHasNonEmptyApplicationPackage`) — GREEN
3. **Non-empty infrastructure** (`eachFinancialModuleHasNonEmptyInfrastructurePackage`) — RED
4. **Domain ∩ Spring/JDBC = ∅** (`domainDoesNotImportSpringOrJdbc`) — GREEN vacuous; `.allowEmptyShould(true)` prevents ArchUnit's fail-on-empty-should default from misfiring before domain classes exist
5. **Application ∩ JDBC = ∅** (`applicationDoesNotImportJdbc`) — RED
6. **API → infrastructure prohibited** (`apiDoesNotDependOnInfrastructure`) — GREEN vacuous
7. **Infrastructure is only JDBC layer** (`infrastructureIsOnlyJdbcLayer`) — RED

`ModuleArchitectureTest` is unchanged: its global `"..domain.."` check is a catch-all across all modules (including identity) and is complementary, not redundant.

---

## Coverage Gate Result (Step 4)

Command: `make test-coverage`

```
> Task :test
199 tests completed, 4 failed  (architecture tests, ignoreFailures in effect)

> Task :riskCoverage FAILED
[ant:jacocoReport] Rule violated for bundle srm-credit-engine-backend: branches covered ratio is 0.91, but expected minimum is 0.95
[ant:jacocoReport] Rule violated for bundle srm-credit-engine-backend: methods covered ratio is 0.94, but expected minimum is 0.95
BUILD FAILED
```

### Coverage gate status: FAIL

**Root cause of coverage shortfall:** The previous worker's `riskCoverageIncludes` replaced the original broad `pricing/**` and `currency/**` selectors with granular sub-package paths AND added `assignor/application/**` and `receivable/application/**`. The additions bring `JdbcAssignorService` and `JdbcReceivableService` (integration-tested paths only) into the measured scope. Their branch coverage from unit tests alone is below the 95% threshold.

- Adding `assignor/application/**` and `receivable/application/**` to scope pulls the bundle ratio down.
- Restoring `currency/**` and `pricing/**` as broad selectors (to preserve root-package classes `Money`, `Rate`, `FxConversionService`, etc.) improved methods from 0.93 → 0.94 but branches remain at 0.91.

**Blocker:** The 95% threshold cannot be met with the new assignor/receivable scope using unit-test exec data alone. The integration test exec file (`integrationTest.exec`, 781 KB) is included but does not fully exercise every branch in those two modules' Jdbc services. This is a pre-existing coverage gap that the new scope exposes; it was not visible before because assignor/receivable were excluded from `riskCoverageIncludes` in HEAD.

**What was tried:**
1. Restored `pricing/**` and `currency/**` broad selectors (methods improved 0.93→0.94, branches still 0.91).
2. Fresh `clean test integrationTest jacocoTestReport riskCoverage` run confirmed the same numbers.

**Not tried (task window closed):** Narrowing the scope back to exclude assignor/receivable application classes until they have full integration coverage, or adjusting the minimum threshold — both require a decision from the orchestrator.

---

## Blockers

| Item | Detail |
|------|--------|
| Coverage gate fails | `riskCoverage` at 0.91 branches / 0.94 methods, threshold 0.95. Exposing assignor/receivable application classes in coverage scope before those modules have full branch coverage breaks the gate. Task 2 integration tests or a scope adjustment needed. |

---

## Build Behavior After `ignoreFailures` Removal

**Change:** `ignoreFailures = true` removed from `tasks.named('test')` in `backend/build.gradle`.

**Expected branch state:** Running `./gradlew test` on this branch will **fail** with 4 architecture test
failures from `FinancialModuleLayeringTest`. This is intentional and expected:

- The 4 red tests (`eachFinancialModuleHasNonEmptyDomainPackage`, `eachFinancialModuleHasNonEmptyInfrastructurePackage`,
  `applicationDoesNotImportJdbc`, `infrastructureIsOnlyJdbcLayer`) encode the target architecture that
  Task 2 must deliver — domain/infrastructure package separation and JDBC confined to infrastructure.
- They are red *by design* because the refactor is incomplete. They will turn green when Task 2 moves
  `Jdbc*Service` classes into `.infrastructure` sub-packages.
- All other unit tests (195 passing) continue to gate the build normally. Any regression in non-architecture
  tests will now be caught immediately, which was masked while `ignoreFailures` was in effect.

**Rationale for removing `ignoreFailures`:** Broad suppression of test failures defeats the purpose of a
CI gate. The correct approach is to carry deliberate red tests with a documented explanation (this note)
rather than silently swallowing all failures. When Task 2 completes, these tests will pass naturally and
no build.gradle change will be needed.
