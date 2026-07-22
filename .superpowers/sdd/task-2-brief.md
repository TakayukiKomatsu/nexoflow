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

