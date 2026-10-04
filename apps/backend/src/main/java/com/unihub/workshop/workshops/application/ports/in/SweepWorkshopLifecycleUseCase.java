package com.unihub.workshop.workshops.application.ports.in;

public interface SweepWorkshopLifecycleUseCase {
    /**
     * Executes the lifecycle sweeper:
     * Acquires distributed lock, atomically transitions expired PUBLISHED workshops to COMPLETED,
     * emits domain events to the transactional outbox, and releases the lock.
     */
    void sweepLifecycle();
}
