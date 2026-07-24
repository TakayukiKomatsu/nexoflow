# Task 2 Report — Separate assignor and receivable persistence

## Commit

```
d5aa3d7 refactor(core): separate assignor and receivable persistence
```

Branch: `fix/case-brief-conformance`

---

## Files Changed (22 files, +961/-54)

### Created — domain

| Path | Purpose |
|------|---------|
| `backend/src/main/java/com/srm/creditengine/assignor/domain/TaxId.java` | Value object; `normalize(String)` strips non-alphanumeric, uppercases, rejects blank |
| `backend/src/main/java/com/srm/creditengine/assignor/domain/Assignor.java` | Pure domain record (id, legalName, taxId, active, createdAt, createdBy) |
| `backend/src/main/java/com/srm/creditengine/receivable/domain/Receivable.java` | Pure domain record (11 fields including audit createdAt/createdBy) |
| `backend/src/main/java/com/srm/creditengine/receivable/domain/ReceivableRegistration.java` | Static `validate(RegisterCommand)` + nested `RegisterCommand` record |

### Created — application ports

| Path | Purpose |
|------|---------|
| `backend/src/main/java/com/srm/creditengine/assignor/application/AssignorRepository.java` | Port: `save`, `findById`, `findAll` |
| `backend/src/main/java/com/srm/creditengine/assignor/application/AssignorApplicationService.java` | Implements `AssignorService`; uses `TaxId.normalize`, `Clock`, delegates to `AssignorRepository`; no JDBC |
| `backend/src/main/java/com/srm/creditengine/receivable/application/ReceivableRepository.java` | Port: `save`, `findById`, `findAll` |
| `backend/src/main/java/com/srm/creditengine/receivable/application/AssignorStatusReader.java` | Port: `isActive(UUID)` — isolates receivable module from assignor infrastructure |
| `backend/src/main/java/com/srm/creditengine/receivable/application/ReceivableApplicationService.java` | Implements `ReceivableService`; calls `ReceivableRegistration.validate`, `AssignorStatusReader`, delegates to `ReceivableRepository`; no JDBC |

### Created — infrastructure adapters

| Path | Purpose |
|------|---------|
| `backend/src/main/java/com/srm/creditengine/assignor/infrastructure/JdbcAssignorRepository.java` | `@Repository`; exact INSERT/SELECT SQL from deleted `JdbcAssignorService` |
| `backend/src/main/java/com/srm/creditengine/receivable/infrastructure/JdbcReceivableRepository.java` | `@Repository`; exact INSERT/SELECT SQL from deleted `JdbcReceivableService` |
| `backend/src/main/java/com/srm/creditengine/receivable/infrastructure/JdbcAssignorStatusReader.java` | `@Repository`; `select active from assignors where id=?` |

### Deleted — obsolete Jdbc*Service implementations

| Path |
|------|
| `backend/src/main/java/com/srm/creditengine/assignor/application/JdbcAssignorService.java` |
| `backend/src/main/java/com/srm/creditengine/receivable/application/JdbcReceivableService.java` |

### Created — tests

| Path | Tests |
|------|-------|
| `backend/src/test/java/com/srm/creditengine/assignor/domain/TaxIdTest.java` | 5 pure-domain tests (normalize, strip, uppercase, blank-reject, punctuation-reject) |
| `backend/src/test/java/com/srm/creditengine/receivable/domain/ReceivableRegistrationTest.java` | 6 pure-domain tests (due-date=issue-date, due-date before, zero amount, negative, >4dp, valid passes) |

### Task 1 artifacts (carried forward, first commit)

| Path |
|------|
| `backend/src/test/java/com/srm/creditengine/architecture/FinancialModuleLayeringTest.java` |
| `backend/build.gradle` (coverage selector + FinancialModuleLayeringTest note) |
| `.superpowers/sdd/task-1-brief.md`, `task-1-report.md`, `task-2-brief.md`, `progress.md` |

---

## Test Results

### Step 2 — domain tests pass (GREEN after implementation)

```
./scripts/with-java21.sh ./backend/gradlew -p backend test \
  --tests '*TaxIdTest' --tests '*ReceivableRegistrationTest'

BUILD SUCCESSFUL in 4s
11 tests completed, 0 failed
```

### Step 5 — focused + controller regression

```
./scripts/with-java21.sh ./backend/gradlew -p backend test \
  --tests '*TaxIdTest' --tests '*ReceivableRegistrationTest' \
  --tests '*ReceivableControllerTest'

BUILD SUCCESSFUL in 5s
```

### make test-api-features

```
make test-api-features
→ ./scripts/with-java21.sh ./backend/gradlew -p backend integrationTest --tests '*RunCucumberTest'

BUILD SUCCESSFUL in 9s
```

---

## Architecture Test Status (FinancialModuleLayeringTest — 7 tests)

| Test method | assignor | receivable | currency | pricing | settlement | Overall |
|-------------|----------|------------|----------|---------|------------|---------|
| eachFinancialModuleHasNonEmptyDomainPackage | ✅ | ✅ | ❌ | ❌ | ❌ | **FAIL** |
| eachFinancialModuleHasNonEmptyApplicationPackage | ✅ | ✅ | ✅ | ✅ | ✅ | PASS |
| eachFinancialModuleHasNonEmptyInfrastructurePackage | ✅ | ✅ | ❌ | ❌ | ❌ | **FAIL** |
| domainDoesNotImportSpringOrJdbc | ✅ | ✅ | ✅ (vacuous) | ✅ (vacuous) | ✅ (vacuous) | PASS |
| applicationDoesNotImportJdbc | ✅ | ✅ | ❌ | ❌ | ❌ | **FAIL** |
| apiDoesNotDependOnInfrastructure | ✅ | ✅ | ✅ (vacuous) | ✅ (vacuous) | ✅ (vacuous) | PASS |
| infrastructureIsOnlyJdbcLayer | ✅ | ✅ | ❌ | ❌ | ❌ | **FAIL** |

Assignor and receivable satisfy **all 7** module-layer rules. Four test methods fail due to currency, pricing, and settlement — the three expected-red modules not in scope for Task 2.

---

## Design Notes

- `AssignorService` and `ReceivableService` interfaces are **unchanged** — controllers compile without modification.
- `ReceivableApplicationService` uses fully-qualified `com.srm.creditengine.receivable.domain.Receivable` to avoid shadowing by `ReceivableService.Receivable` (a nested record visible via the implemented interface).
- `@Transactional` is on the application service write methods; repository classes carry `@Repository` but no transaction annotation (follows Spring convention — JdbcTemplate ops are non-transactional by default; the enclosing service transaction controls the boundary).
- Domain `Assignor` and `Receivable` carry `createdBy` for the write path; reads return `null` for this field since the SELECT statements do not project it.
