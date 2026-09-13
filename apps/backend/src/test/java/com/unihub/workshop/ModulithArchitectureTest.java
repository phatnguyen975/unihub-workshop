package com.unihub.workshop;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noConstructors;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

/**
 * Architectural regression guard for the Spring Modulith application.
 * Verifies that inter-module dependencies and intra-module layering strictly adhere to
 * Clean Architecture and Spring Modulith boundaries, preventing coupling, layer bleed,
 * and cyclic dependencies across all current and future business modules.
 */
class ModulithArchitectureTest {

    private static final ApplicationModules modules = ApplicationModules.of(
        WorkshopApplication.class
    );

    private static final List<String> BUSINESS_MODULES = List.of(
        "iam",
        "workshops",
        "registrations",
        "payments",
        "attendances",
        "notifications",
        "search"
    );

    private static final String[] BUSINESS_MODULE_PACKAGES =
        BUSINESS_MODULES.stream()
            .map(module -> "com.unihub.workshop." + module + "..")
            .toArray(String[]::new);

    private static JavaClasses importedClasses;

    @BeforeAll
    static void setUp() {
        importedClasses = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.unihub.workshop");
    }

    // =========================================================================
    // 1. Spring Modulith Encapsulation & Cyclic Dependency Check
    // =========================================================================

    @Test
    @DisplayName(
        "Verify that Spring Modulith module boundaries are respected and no cycle exists"
    )
    void verifiesModularStructure() {
        modules.verify();
    }

    // =========================================================================
    // 2. Clean Architecture: Layer Purity & Dependency Inversion (DIP)
    // =========================================================================

    @Test
    @DisplayName(
        "Domain layers must not depend on application, infrastructure, presentation, or public api"
    )
    void domainMustNotDependOnOuterLayers() {
        noClasses()
            .that()
            .resideInAPackage("..domain..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                "..application..",
                "..infrastructure..",
                "..presentation..",
                "..api.."
            )
            .because(
                "Domain layer must remain the innermost layer in Clean Architecture and never depend on outer layers"
            )
            .check(importedClasses);
    }

    @Test
    @DisplayName(
        "Domain layers must be pure Java, independent of frameworks, persistence, and database drivers"
    )
    void domainMustBePureJava() {
        noClasses()
            .that()
            .resideInAPackage("..domain..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                "org.springframework..",
                "jakarta.persistence..",
                "jakarta.servlet..",
                "jakarta.transaction..",
                "java.sql..",
                "javax.sql.."
            )
            .because(
                "Domain layer must be 100% pure Java and independent of frameworks, JPA entities, and database technologies"
            )
            .check(importedClasses);
    }

    @Test
    @DisplayName(
        "Application layers must not depend on infrastructure or presentation"
    )
    void applicationMustNotDependOnInfrastructureOrPresentation() {
        noClasses()
            .that()
            .resideInAPackage("..application..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("..infrastructure..", "..presentation..")
            .because("Application layer must not depend on outer layers (DIP)")
            .check(importedClasses);
    }

    @Test
    @DisplayName(
        "Application layers must not depend on persistence entities or web servlets"
    )
    void applicationMustNotDependOnPersistenceOrServlet() {
        noClasses()
            .that()
            .resideInAPackage("..application..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("jakarta.persistence..", "jakarta.servlet..")
            .because(
                "Application layer must operate on domain models and ports, never database entities or HTTP servlets"
            )
            .check(importedClasses);
    }

    @Test
    @DisplayName("Outbound application ports must be interfaces")
    void outboundPortsMustBeInterfaces() {
        classes()
            .that()
            .resideInAPackage("..application.ports.out..")
            .should()
            .beInterfaces()
            .because(
                "Outbound ports must define abstract contracts for Dependency Inversion, never concrete implementations"
            )
            .check(importedClasses);
    }

    @Test
    @DisplayName("Infrastructure layers must not depend on presentation")
    void infrastructureMustNotDependOnPresentation() {
        noClasses()
            .that()
            .resideInAPackage("..infrastructure..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..presentation..")
            .because(
                "Infrastructure adapters must never depend on HTTP presentation controllers or web DTOs"
            )
            .check(importedClasses);
    }

    @Test
    @DisplayName(
        "Presentation layers must not depend on infrastructure or persistence directly"
    )
    void presentationMustNotDependOnInfrastructureOrPersistence() {
        noClasses()
            .that()
            .resideInAPackage("..presentation..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("..infrastructure..", "jakarta.persistence..")
            .because(
                "Presentation layer must only communicate via Application use cases or API facades, never directly with infrastructure or JPA"
            )
            .check(importedClasses);
    }

    @Test
    @DisplayName(
        "Public Module API packages must remain pure public contracts and not depend on module internals or persistence"
    )
    void apiMustNotDependOnModuleInternalsOrPersistence() {
        noClasses()
            .that()
            .resideInAPackage("..api..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                "..application..",
                "..infrastructure..",
                "..presentation..",
                "jakarta.persistence.."
            )
            .because(
                "Public Module API contracts (facades, DTO records, events) must not depend on application orchestration, infrastructure adapters, presentation controllers, or persistence entities"
            )
            .check(importedClasses);
    }

    // =========================================================================
    // 3. Inter-Module Boundaries & Encapsulation
    // =========================================================================

    @Test
    @DisplayName(
        "Cross-module communication must strictly go through the public api package of each module"
    )
    void crossModuleAccessMustOnlyGoThroughApi() {
        for (String module : BUSINESS_MODULES) {
            noClasses()
                .that()
                .resideOutsideOfPackage("com.unihub.workshop." + module + "..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                    "com.unihub.workshop." + module + ".domain..",
                    "com.unihub.workshop." + module + ".application..",
                    "com.unihub.workshop." + module + ".infrastructure..",
                    "com.unihub.workshop." + module + ".presentation.."
                )
                .because(
                    "Cross-module communication into " +
                        module +
                        " must only go through com.unihub.workshop." +
                        module +
                        ".api.."
                )
                .check(importedClasses);
        }
    }

    @Test
    @DisplayName(
        "IAM module must remain foundational and not depend on any other business module"
    )
    void iamMustNotDependOnOtherBusinessModules() {
        noClasses()
            .that()
            .resideInAPackage("com.unihub.workshop.iam..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                "com.unihub.workshop.workshops..",
                "com.unihub.workshop.registrations..",
                "com.unihub.workshop.payments..",
                "com.unihub.workshop.attendances..",
                "com.unihub.workshop.notifications..",
                "com.unihub.workshop.search.."
            )
            .because(
                "IAM module must remain foundational and not depend on any other business module"
            )
            .check(importedClasses);
    }

    @Test
    @DisplayName(
        "Operational write-model modules must not depend on the CQRS search read model"
    )
    void writeModulesMustNotDependOnCqrsSearch() {
        noClasses()
            .that()
            .resideInAnyPackage(
                "com.unihub.workshop.iam..",
                "com.unihub.workshop.workshops..",
                "com.unihub.workshop.registrations..",
                "com.unihub.workshop.payments..",
                "com.unihub.workshop.attendances..",
                "com.unihub.workshop.notifications.."
            )
            .should()
            .dependOnClassesThat()
            .resideInAPackage("com.unihub.workshop.search..")
            .because(
                "Transactional write-model modules must never depend on the asynchronous CQRS search read model"
            )
            .check(importedClasses);
    }

    @Test
    @DisplayName(
        "Shared kernel must remain domain-agnostic and never depend on any business domain modules"
    )
    void sharedKernelMustNotDependOnBusinessModules() {
        noClasses()
            .that()
            .resideInAPackage("com.unihub.workshop.shared..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(BUSINESS_MODULE_PACKAGES)
            .because(
                "Shared kernel must remain domain-agnostic and free of upwards business dependencies"
            )
            .check(importedClasses);
    }

    // =========================================================================
    // 4. Dependency Injection & Coding Standards
    // =========================================================================

    @Test
    @DisplayName(
        "Prohibit @Autowired across the entire codebase (use constructor injection)"
    )
    void prohibitAutowired() {
        noFields()
            .should()
            .beAnnotatedWith(
                "org.springframework.beans.factory.annotation.Autowired"
            )
            .because(
                "Field injection via @Autowired is prohibited; use single constructor injection"
            )
            .check(importedClasses);

        noMethods()
            .should()
            .beAnnotatedWith(
                "org.springframework.beans.factory.annotation.Autowired"
            )
            .because(
                "Method injection via @Autowired is prohibited; use single constructor injection"
            )
            .check(importedClasses);

        noConstructors()
            .should()
            .beAnnotatedWith(
                "org.springframework.beans.factory.annotation.Autowired"
            )
            .because(
                "Constructor @Autowired annotation is prohibited; single constructor injection is implicit"
            )
            .check(importedClasses);
    }

    @Test
    @Disabled("Run on demand to generate architecture documentation")
    void createModuleDocumentation() {
        new Documenter(modules)
            .writeDocumentation()
            .writeModulesAsPlantUml()
            .writeIndividualModulesAsPlantUml();
    }
}
