package com.unihub.workshop.workers.maintenance.core.ports.in;

public interface RunTtlCleanup {
    void cleanExpiredTokens();
}
