package com.unihub.workshop.workers;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * Baseline sanity test for the workers application entry point.
 */
class WorkersApplicationTests {

    @Test
    void applicationClassIsInstantiable() {
        assertDoesNotThrow(WorkersApplication::new);
    }
}
