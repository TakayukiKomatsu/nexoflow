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

