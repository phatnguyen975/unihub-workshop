package com.unihub.workshop;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * Baseline sanity test for the backend application entry point.
 */
class WorkshopApplicationTests {

    @Test
    void applicationClassIsInstantiable() {
        assertDoesNotThrow(WorkshopApplication::new);
    }
}
