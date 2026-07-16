package com.srm.creditengine.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

class ModuleArchitectureTest {
    @Test
    void domainCodeDoesNotDependOnFrameworkOrPersistenceAdapters() {
        var classes = new ClassFileImporter().importPackages("com.srm.creditengine");

        noClasses()
                .that()
                .resideInAPackage("..domain..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "org.springframework..", "jakarta.persistence..", "java.sql..", "javax.sql..")
                .check(classes);
    }
}
