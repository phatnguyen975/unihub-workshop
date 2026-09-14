package com.unihub.workshop;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import org.junit.jupiter.api.Test;

/**
 * Baseline sanity test for the backend application entry point.
 */
class WorkshopApplicationTests {

    @Test
    void applicationClassIsInstantiable() {
        assertDoesNotThrow(WorkshopApplication::new);
    }
}
