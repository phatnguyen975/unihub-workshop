package com.unihub.workshop.shared.idempotency;

import com.unihub.workshop.shared.exception.AppException;
import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * Thrown when an in-flight idempotent request is already being processed.
 * Carries a retry-after duration (e.g., 2 seconds).
 */
public class IdempotencyConflictException extends AppException {

    private final Integer retryAfterSeconds;

    public IdempotencyConflictException(
        String message,
        Integer retryAfterSeconds
    ) {
        super(
            HttpStatus.CONFLICT,
            "urn:unihub:error:conflict",
            message,
            retryAfterSeconds != null
                ? Map.of("retryAfter", retryAfterSeconds)
                : null
        );
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public Integer getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
