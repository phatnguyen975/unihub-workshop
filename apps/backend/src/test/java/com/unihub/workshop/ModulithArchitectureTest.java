package com.unihub.workshop;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noConstructors;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

/**
 * Architectural regression guard for the Spring Modulith application.
 * Verifies that inter-module dependencies adhere to strict Clean Architecture boundaries,
 * preventing tight coupling, layer bleed, and circular dependencies.
 */
class ModulithArchitectureTest {

    private static final ApplicationModules modules = ApplicationModules.of(
        WorkshopApplication.class
    );

    private static JavaClasses importedClasses;

    @BeforeAll
    static void setUp() {
        importedClasses = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.unihub.workshop");
    }

    @Test
    @DisplayName("Verify Spring Modulith modular structure and encapsulation")
    void verifiesModularStructure() {
        // Verifies that module boundaries are respected and no cycle exists
        modules.verify();
    }

    @Test
    @DisplayName("Domain layers must not depend on application, infrastructure, or presentation")
    void domainMustNotDependOnOuterLayers() {
        noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("..application..", "..infrastructure..", "..presentation..")
            .because("Domain layer must remain the innermost layer in Clean Architecture")
            .check(importedClasses);
    }

    @Test
    @DisplayName("Domain layers must be pure Java, independent of frameworks and persistence")
    void domainMustBePureJava() {
        noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("org.springframework..", "jakarta.persistence..", "jakarta.servlet..")
            .because("Domain layer must be pure Java and independent of frameworks and persistence technologies")
            .check(importedClasses);
    }

    @Test
    @DisplayName("Application layers must not depend on infrastructure or presentation")
    void applicationMustNotDependOnInfrastructureOrPresentation() {
        noClasses()
            .that().resideInAPackage("..application..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("..infrastructure..", "..presentation..")
            .because("Application layer must not depend on outer layers (DIP)")
            .check(importedClasses);
    }

    @Test
    @DisplayName("Presentation layers must not depend on infrastructure directly")
    void presentationMustNotDependOnInfrastructure() {
        noClasses()
            .that().resideInAPackage("..presentation..")
            .should().dependOnClassesThat()
            .resideInAPackage("..infrastructure..")
            .because("Presentation layer must only communicate via Application use cases or API facades, never directly with infrastructure")
            .check(importedClasses);
    }

    @Test
    @DisplayName("Public Module API packages must not depend on infrastructure or persistence")
    void apiMustNotDependOnInfrastructureOrPersistence() {
        noClasses()
            .that().resideInAPackage("..api..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("..infrastructure..", "jakarta.persistence..")
            .because("Public Module API contracts must not leak infrastructure details or persistence entities")
            .check(importedClasses);
    }

    @Test
    @DisplayName("Cross-module communication must only go through the public api package")
    void crossModuleAccessMustOnlyGoThroughApi() {
        noClasses()
            .that().resideInAPackage("com.unihub.workshop.workshops..")
            .should().dependOnClassesThat()
            .resideInAnyPackage(
                "com.unihub.workshop.iam.domain..",
                "com.unihub.workshop.iam.application..",
                "com.unihub.workshop.iam.infrastructure..",
                "com.unihub.workshop.iam.presentation.."
            )
            .because("Cross-module communication between workshops and iam must only go through com.unihub.workshop.iam.api..")
            .check(importedClasses);

        noClasses()
            .that().resideInAPackage("com.unihub.workshop.iam..")
            .should().dependOnClassesThat()
            .resideInAPackage("com.unihub.workshop.workshops..")
            .because("IAM module must not depend on workshops module")
            .check(importedClasses);
    }

    @Test
    @DisplayName("Shared kernel must not depend on any business domain modules")
    void sharedKernelMustNotDependOnBusinessModules() {
        noClasses()
            .that().resideInAPackage("com.unihub.workshop.shared..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("com.unihub.workshop.iam..", "com.unihub.workshop.workshops..")
            .because("Shared kernel must remain domain-agnostic and free of upwards business dependencies")
            .check(importedClasses);
    }

    @Test
    @DisplayName("Prohibit @Autowired across the entire codebase (use constructor injection)")
    void prohibitAutowired() {
        noFields()
            .should().beAnnotatedWith("org.springframework.beans.factory.annotation.Autowired")
            .because("Field injection via @Autowired is prohibited; use single constructor injection")
            .check(importedClasses);

        noMethods()
            .should().beAnnotatedWith("org.springframework.beans.factory.annotation.Autowired")
            .because("Method injection via @Autowired is prohibited; use single constructor injection")
            .check(importedClasses);

        noConstructors()
            .should().beAnnotatedWith("org.springframework.beans.factory.annotation.Autowired")
            .because("Constructor @Autowired annotation is prohibited; single constructor injection is implicit")
            .check(importedClasses);
    }

    @Test
    @Disabled("Run on demand to generate architecture documentation")
    void createModuleDocumentation() {
        // Generates C4 component diagrams and Asciidoc summaries
        new Documenter(modules)
            .writeDocumentation()
            .writeModulesAsPlantUml()
            .writeIndividualModulesAsPlantUml();
    }
}
