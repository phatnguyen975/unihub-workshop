package com.unihub.workshop.shared.idempotency;

/** Controls whether a durable failed key can be claimed again. */
public enum FailedKeyPolicy {
    REJECT,
    RECLAIM,
}
