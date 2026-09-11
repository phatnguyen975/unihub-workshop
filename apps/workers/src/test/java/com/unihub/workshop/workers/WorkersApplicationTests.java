package com.unihub.workshop.workers;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import org.junit.jupiter.api.Test;

/**
 * Baseline sanity test for the workers application entry point.
 */
class WorkersApplicationTests {

    @Test
    void applicationClassIsInstantiable() {
        assertDoesNotThrow(WorkersApplication::new);
    }
}
