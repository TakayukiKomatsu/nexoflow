package com.srm.creditengine.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * Executable layering contract for the five financial modules.
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
        classes = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("com.srm.creditengine");
    }

    // ── Rule 1a ─────────────────────────────────────────────────────────────────────────────────
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

    @Test
    void eachFinancialModuleHasNonEmptyInfrastructurePackage() {
        for (String module : MODULES) {
            assertThat(classes.stream().anyMatch(c ->
                    c.getPackageName().startsWith("com.srm.creditengine." + module + ".infrastructure")))
                    .as("module '%s' must have at least one class in an .infrastructure sub-package", module)
                    .isTrue();
        }
    }

    @Test
    void financialModulesDoNotHideTypesOutsideTheirDeclaredLayers() {
        for (String module : MODULES) {
            assertThat(classes.stream()
                            .filter(candidate -> candidate.getPackageName()
                                    .equals("com.srm.creditengine." + module))
                            .map(candidate -> candidate.getName())
                            .toList())
                    .as("module '%s' must place every production type in api, application, domain, or infrastructure", module)
                    .isEmpty();
        }
    }

    // ── Rule 2 ──────────────────────────────────────────────────────────────────────────────────

    /** Keeps the domain model free of framework and persistence dependencies. */
    @Test
    void domainDoesNotImportSpringOrJdbc() {
        for (String module : MODULES) {
            noClasses().that().resideInAPackage(".." + module + ".domain..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "org.springframework..", "java.sql..", "javax.sql..")
                    .check(classes);
        }
    }

    @Test
    void domainDoesNotDependOnOuterLayers() {
        for (String module : MODULES) {
            noClasses().that().resideInAPackage(".." + module + ".domain..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "..api..", "..application..", "..infrastructure..")
                    .check(classes);
        }
    }

    // ── Rule 3 ──────────────────────────────────────────────────────────────────────────────────

    /** Keeps raw database access out of application use cases. */
    @Test
    void applicationDoesNotImportJdbc() {
        for (String module : MODULES) {
            noClasses().that().resideInAPackage(".." + module + ".application..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "org.springframework.jdbc..", "java.sql..", "javax.sql..")
                    .check(classes);
        }
    }

    @Test
    void applicationDoesNotDependOnApiOrInfrastructure() {
        for (String module : MODULES) {
            noClasses().that().resideInAPackage(".." + module + ".application..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "..api..", "..infrastructure..")
                    .check(classes);
        }
    }

    // ── Rule 4 ──────────────────────────────────────────────────────────────────────────────────

    /** Prevents API controllers from bypassing application ports. */
    @Test
    void apiDoesNotDependOnInfrastructure() {
        for (String module : MODULES) {
            noClasses().that().resideInAPackage(".." + module + ".api..")
                    .should().dependOnClassesThat().resideInAPackage(".." + module + ".infrastructure..")
                    .check(classes);
        }
    }

    // ── Rule 5 ──────────────────────────────────────────────────────────────────────────────────

    /** Ensures only infrastructure adapters can depend on JDBC. */
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
