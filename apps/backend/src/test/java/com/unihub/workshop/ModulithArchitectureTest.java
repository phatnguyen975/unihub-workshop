package com.unihub.workshop;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

/**
 * Architectural regression guard for the Spring Modulith application.
 * Verifies that inter-module dependencies adhere to strict boundaries,
 * preventing tight coupling and circular dependencies.
 */
class ModulithArchitectureTest {

    ApplicationModules modules = ApplicationModules.of(
        WorkshopApplication.class
    );

    @Test
    void verifiesModularStructure() {
        // Verifies that module boundaries are respected
        modules.verify();
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
