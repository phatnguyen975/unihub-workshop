package com.unihub.workshop.workers;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Architectural regression guard for the background Workers application.
 * Enforces Hexagonal Architecture (Ports and Adapters) boundaries within every worker module,
 * as well as strict isolation between distinct worker modules and the shared kernel,
 * ensuring modularity, dependency inversion, and domain purity across all current and future worker pipelines.
 */
class HexagonalArchitectureTest {

    private static final List<String> WORKER_MODULES = List.of(
        "batch_ingestion",
        "maintenance",
        "notifications",
        "search_sync",
        "ai_processing"
    );

    private static final String[] WORKER_MODULE_PACKAGES =
        WORKER_MODULES.stream()
            .map(module -> "com.unihub.workshop.workers." + module + "..")
            .toArray(String[]::new);

    private static JavaClasses importedClasses;

    @BeforeAll
    static void setUp() {
        importedClasses = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.unihub.workshop.workers");
    }

    // =========================================================================
    // 1. Hexagonal Architecture: Core Isolation (Inside of the Hexagon)
    // =========================================================================

    @Test
    @DisplayName("Core layer must never depend on inbound or outbound adapters")
    void coreMustNotDependOnAdapters() {
        noClasses()
            .that()
            .resideInAPackage("..core..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("..inbound..", "..outbound..")
            .because(
                "In Hexagonal Architecture, the core represents the application interior and must never depend on inbound or outbound adapters"
            )
            .check(importedClasses);
    }

    @Test
    @DisplayName(
        "Core domain models must remain pure Java, free of frameworks, persistence, and messaging drivers"
    )
    void coreModelsMustBePureJava() {
        noClasses()
            .that()
            .resideInAnyPackage("..core.models..", "..core.model..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                "org.springframework..",
                "jakarta.persistence..",
                "software.amazon.awssdk..",
                "com.rabbitmq..",
                "org.springframework.amqp.."
            )
            .because(
                "Core domain models must remain pure Java records or entities, independent of frameworks and I/O libraries"
            )
            .check(importedClasses);
    }

    // =========================================================================
    // 2. Hexagonal Architecture: Adapter Decoupling & Port Abstraction
    // =========================================================================

    @Test
    @DisplayName(
        "Inbound adapters must never depend directly on outbound adapters"
    )
    void inboundMustNotDependOnOutbound() {
        noClasses()
            .that()
            .resideInAPackage("..inbound..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..outbound..")
            .because(
                "Inbound driving adapters must not depend on outbound driven adapters; interactions must be mediated through core"
            )
            .check(importedClasses);
    }

    @Test
    @DisplayName("Outbound adapters must never depend on inbound adapters")
    void outboundMustNotDependOnInbound() {
        noClasses()
            .that()
            .resideInAPackage("..outbound..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..inbound..")
            .because(
                "Outbound driven adapters must never depend on inbound driving adapters"
            )
            .check(importedClasses);
    }

    @Test
    @DisplayName("Inbound ports must be interfaces")
    void inboundPortsMustBeInterfaces() {
        classes()
            .that()
            .resideInAPackage("..core.ports.in..")
            .and()
            .areTopLevelClasses()
            .should()
            .beInterfaces()
            .because(
                "Inbound ports define use-case boundaries and driver contracts and must be interfaces"
            )
            .check(importedClasses);
    }

    @Test
    @DisplayName("Outbound ports must be interfaces")
    void outboundPortsMustBeInterfaces() {
        classes()
            .that()
            .resideInAPackage("..core.ports.out..")
            .and()
            .areTopLevelClasses()
            .should()
            .beInterfaces()
            .because(
                "Outbound ports define SPI contracts for driven adapters and must be interfaces"
            )
            .check(importedClasses);
    }

    @Test
    @DisplayName(
        "Inbound adapters must not depend directly on concrete use case implementations"
    )
    void inboundMustNotDependOnConcreteUseCases() {
        noClasses()
            .that()
            .resideInAPackage("..inbound..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..core.usecases..")
            .because(
                "Inbound adapters must drive the core via inbound port interfaces, never depending directly on concrete use case classes"
            )
            .check(importedClasses);
    }

    @Test
    @DisplayName(
        "Outbound adapters must only implement ports and never depend on core use case implementations"
    )
    void outboundAdaptersMustNotDependOnUseCases() {
        noClasses()
            .that()
            .resideInAnyPackage(
                "..outbound.persistence..",
                "..outbound.storage..",
                "..outbound.messaging..",
                "..outbound.ai_providers.."
            )
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..core.usecases..")
            .because(
                "Outbound adapters must only implement outbound ports and must never depend on core usecase implementations"
            )
            .check(importedClasses);
    }

    // =========================================================================
    // 3. Inter-Worker-Module Boundaries & Shared Kernel Isolation
    // =========================================================================

    @Test
    @DisplayName(
        "Worker business modules must be completely isolated from one another"
    )
    void workerModulesMustBeIsolatedFromEachOther() {
        for (String module : WORKER_MODULES) {
            String currentModulePackage =
                "com.unihub.workshop.workers." + module + "..";
            String[] otherModulePackages = WORKER_MODULES.stream()
                .filter(m -> !m.equals(module))
                .map(m -> "com.unihub.workshop.workers." + m + "..")
                .toArray(String[]::new);

            noClasses()
                .that()
                .resideInAPackage(currentModulePackage)
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(otherModulePackages)
                .because(
                    "Worker module '" +
                        module +
                        "' must be completely autonomous and decoupled from other worker modules"
                )
                .allowEmptyShould(true)
                .check(importedClasses);
        }
    }

    @Test
    @DisplayName(
        "Shared worker kernel must never depend on any worker business module"
    )
    void sharedKernelMustNotDependOnWorkerModules() {
        noClasses()
            .that()
            .resideInAPackage("com.unihub.workshop.workers.shared..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(WORKER_MODULE_PACKAGES)
            .because(
                "Shared worker infrastructure must remain completely agnostic of business worker modules"
            )
            .check(importedClasses);
    }

    // =========================================================================
    // 4. Persistence Encapsulation & Quality Rules
    // =========================================================================

    @Test
    @DisplayName(
        "Inbound adapters must never directly access persistence repositories or entity managers"
    )
    void inboundMustNotDirectlyAccessPersistence() {
        noClasses()
            .that()
            .resideInAPackage("..inbound..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                "org.springframework.data.repository..",
                "jakarta.persistence.EntityManager"
            )
            .because(
                "Inbound adapters must trigger business logic via inbound ports and never access persistence repositories directly"
            )
            .check(importedClasses);
    }

    @Test
    @DisplayName(
        "Spring Data JPA repositories must be strictly encapsulated within persistence adapters"
    )
    void persistenceRepositoriesMustBeEncapsulated() {
        classes()
            .that()
            .resideInAPackage("..outbound.persistence..")
            .and()
            .areAssignableTo(
                org.springframework.data.repository.Repository.class
            )
            .should()
            .onlyBeAccessed()
            .byClassesThat()
            .resideInAPackage("..outbound.persistence..")
            .because(
                "Spring Data JPA repositories must be encapsulated within the persistence adapter and never leaked outside"
            )
            .check(importedClasses);
    }

    @Test
    @DisplayName(
        "Constructor injection is mandatory; field injection via @Autowired is strictly forbidden"
    )
    void noFieldInjectionAnywhere() {
        noFields()
            .should()
            .beAnnotatedWith(
                "org.springframework.beans.factory.annotation.Autowired"
            )
            .because(
                "Constructor injection is mandatory across all worker classes; field injection with @Autowired is strictly forbidden"
            )
            .check(importedClasses);
    }
}
