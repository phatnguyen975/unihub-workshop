package com.unihub.workshop;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

class ModulithArchitectureTest {

    @Test
    void verifiesModularStructure() {
        ApplicationModules modules = ApplicationModules.of(WorkshopApplication.class);
        
        // Verifies that module boundaries are respected
        modules.verify();
    }
    
    @Test
    void createModuleDocumentation() {
        ApplicationModules modules = ApplicationModules.of(WorkshopApplication.class);
        
        // Generates C4 component diagrams and Asciidoc summaries
        new Documenter(modules)
            .writeDocumentation()
            .writeModulesAsPlantUml()
            .writeIndividualModulesAsPlantUml();
    }
}
