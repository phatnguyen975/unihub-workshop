package com.unihub.workshop.workshops.application.ports.in;

public interface ReconcileWorkshopCapacityUseCase {
    /**
     * Executes the capacity reconciliation job:
     * Acquires distributed lock, retrieves active PUBLISHED workshops and their active registrations,
     * computes ground truth, compares with Redis capacity counters, corrects any drift,
     * logs summary, and releases the lock.
     */
    void reconcileCapacity();
}
