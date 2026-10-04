package com.unihub.workshop.workshops.application.ports.out;

import java.time.Duration;

public interface WorkshopLockPort {
    boolean tryLock(String lockKey, Duration ttl);

    void releaseLock(String lockKey);
}
