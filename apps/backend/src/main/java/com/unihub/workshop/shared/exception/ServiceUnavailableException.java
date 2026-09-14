package com.unihub.workshop.shared.exception;

import java.util.Map;
import org.springframework.http.HttpStatus;

public class ServiceUnavailableException extends AppException {

    private final Integer retryAfterSeconds;

    public ServiceUnavailableException(String message) {
        super(
            HttpStatus.SERVICE_UNAVAILABLE,
            "urn:unihub:error:service-unavailable",
            message
        );
        this.retryAfterSeconds = null;
    }

    public ServiceUnavailableException(
        String message,
        Integer retryAfterSeconds
    ) {
        super(
            HttpStatus.SERVICE_UNAVAILABLE,
            "urn:unihub:error:service-unavailable",
            message
        );
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public ServiceUnavailableException(String message, Throwable cause) {
        super(
            HttpStatus.SERVICE_UNAVAILABLE,
            "urn:unihub:error:service-unavailable",
            message,
            cause
        );
        this.retryAfterSeconds = null;
    }

    public ServiceUnavailableException(
        String message,
        Integer retryAfterSeconds,
        Throwable cause
    ) {
        super(
            HttpStatus.SERVICE_UNAVAILABLE,
            "urn:unihub:error:service-unavailable",
            message,
            cause
        );
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public ServiceUnavailableException(String type, String message) {
        super(HttpStatus.SERVICE_UNAVAILABLE, type, message);
        this.retryAfterSeconds = null;
    }

    public ServiceUnavailableException(
        String type,
        String message,
        Integer retryAfterSeconds
    ) {
        super(HttpStatus.SERVICE_UNAVAILABLE, type, message);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public ServiceUnavailableException(
        String type,
        String message,
        Map<String, Object> properties
    ) {
        super(HttpStatus.SERVICE_UNAVAILABLE, type, message, properties);
        this.retryAfterSeconds = null;
    }

    public ServiceUnavailableException(
        String type,
        String message,
        Map<String, Object> properties,
        Integer retryAfterSeconds
    ) {
        super(HttpStatus.SERVICE_UNAVAILABLE, type, message, properties);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public Integer getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
