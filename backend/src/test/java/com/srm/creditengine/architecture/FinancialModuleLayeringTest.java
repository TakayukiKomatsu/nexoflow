package com.srm.creditengine.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * Red contract for Task 1. Establishes the required layering boundary for the five financial
 * modules. Failing tests (marked RED below) document what Task 2 must refactor; passing tests
 * (marked GREEN) confirm the baseline that already holds.
 *
 * <p>Required boundary (task-1-brief.md interface):
 * <ol>
 *   <li>Each module has non-empty domain, application, and infrastructure sub-packages.
 *   <li>Domain imports neither Spring nor JDBC.
 *   <li>Application imports neither {@code JdbcTemplate} nor {@code java.sql}.
 *   <li>API depends only on application/domain contracts, not on infrastructure.
 *   <li>Infrastructure is the only JDBC layer within each module.
 * </ol>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FinancialModuleLayeringTest {

    private static final List<String> MODULES =
            List.of("assignor", "receivable", "currency", "pricing", "settlement");

    private JavaClasses classes;

    @BeforeAll
    void loadClasses() {
        classes = new ClassFileImporter().importPackages("com.srm.creditengine");
    }

    // ── Rule 1a ─────────────────────────────────────────────────────────────────────────────────

    /**
     * RED: none of the five financial modules has a {@code domain} sub-package yet.
     * Task 2 must introduce domain value-objects / entities in each module.
     */
    @Test
    void eachFinancialModuleHasNonEmptyDomainPackage() {
        for (String module : MODULES) {
            assertThat(classes.stream().anyMatch(c ->
                    c.getPackageName().startsWith("com.srm.creditengine." + module + ".domain")))
                    .as("module '%s' must have at least one class in a .domain sub-package", module)
                    .isTrue();
        }
    }

    // ── Rule 1b ─────────────────────────────────────────────────────────────────────────────────

    /**
     * GREEN: all five modules already have an {@code application} sub-package.
     * Verifies the baseline layer is populated before the refactor.
     */
    @Test
    void eachFinancialModuleHasNonEmptyApplicationPackage() {
        for (String module : MODULES) {
            assertThat(classes.stream().anyMatch(c ->
                    c.getPackageName().startsWith("com.srm.creditengine." + module + ".application")))
                    .as("module '%s' must have at least one class in an .application sub-package", module)
                    .isTrue();
        }
    }

    // ── Rule 1c ─────────────────────────────────────────────────────────────────────────────────

    /**
     * RED: none of the five financial modules has an {@code infrastructure} sub-package yet.
     * Task 2 must create infrastructure adapters (e.g. {@code JdbcAssignorRepository}) and move
     * all Jdbc* classes out of the application layer.
     */
    @Test
    void eachFinancialModuleHasNonEmptyInfrastructurePackage() {
        for (String module : MODULES) {
            assertThat(classes.stream().anyMatch(c ->
                    c.getPackageName().startsWith("com.srm.creditengine." + module + ".infrastructure")))
                    .as("module '%s' must have at least one class in an .infrastructure sub-package", module)
                    .isTrue();
        }
    }

    // ── Rule 2 ──────────────────────────────────────────────────────────────────────────────────

    /**
     * GREEN vacuously (no domain sub-packages exist yet, so no violating classes are found).
     * {@code allowEmptyShould(true)} suppresses ArchUnit's fail-on-empty-should default so
     * this rule documents the intent without false-failing when domain classes don't yet exist.
     * Once Task 2 introduces domain classes this rule actively prevents Spring and JDBC
     * from contaminating the pure domain model.
     */
    @Test
    void domainDoesNotImportSpringOrJdbc() {
        for (String module : MODULES) {
            noClasses().that().resideInAPackage(".." + module + ".domain..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "org.springframework..", "java.sql..", "javax.sql..")
                    .allowEmptyShould(true)
                    .check(classes);
        }
    }

    // ── Rule 3 ──────────────────────────────────────────────────────────────────────────────────

    /**
     * RED: {@code JdbcAssignorService}, {@code JdbcCurrencyService}, {@code JdbcReferenceRateService},
     * {@code JdbcReceivableService}, {@code AuthoritativePricingService}, and
     * {@code JdbcSettlementService} all reside in their module's {@code application} layer and
     * import {@code JdbcTemplate} or {@code java.sql}.
     * Task 2 must move these adapters to {@code infrastructure}.
     */
    @Test
    void applicationDoesNotImportJdbc() {
        for (String module : MODULES) {
            noClasses().that().resideInAPackage(".." + module + ".application..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "org.springframework.jdbc..", "java.sql..", "javax.sql..")
                    .check(classes);
        }
    }

    // ── Rule 4 ──────────────────────────────────────────────────────────────────────────────────

    /**
     * GREEN vacuously (no infrastructure sub-packages exist yet).
     * Once Task 2 introduces infrastructure classes this rule prevents API controllers from
     * bypassing the application ports to reach persistence adapters directly.
     */
    @Test
    void apiDoesNotDependOnInfrastructure() {
        for (String module : MODULES) {
            noClasses().that().resideInAPackage(".." + module + ".api..")
                    .should().dependOnClassesThat().resideInAPackage(".." + module + ".infrastructure..")
                    .check(classes);
        }
    }

    // ── Rule 5 ──────────────────────────────────────────────────────────────────────────────────

    /**
     * RED: Jdbc* service classes reside in {@code application}, not {@code infrastructure}.
     * Any class within a financial module that lives outside its own {@code infrastructure}
     * sub-package must not import JDBC types. After Task 2 moves the adapters, only the
     * {@code infrastructure} layer will remain as the JDBC boundary.
     */
    @Test
    void infrastructureIsOnlyJdbcLayer() {
        for (String module : MODULES) {
            noClasses().that()
                    .resideInAPackage("com.srm.creditengine." + module + "..")
                    .and().resideOutsideOfPackage("com.srm.creditengine." + module + ".infrastructure..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "org.springframework.jdbc..", "java.sql..", "javax.sql..")
                    .check(classes);
        }
    }
}
