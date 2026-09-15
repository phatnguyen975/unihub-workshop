package com.unihub.workshop.shared.exception;

import java.util.Map;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class AppException extends RuntimeException {

    private final HttpStatus status;
    private final String type;
    private final Map<String, Object> properties;

    public AppException(HttpStatus status, String type, String message) {
        super(message);
        this.status = status;
        this.type = type;
        this.properties = null;
    }

    public AppException(
        HttpStatus status,
        String type,
        String message,
        Throwable cause
    ) {
        super(message, cause);
        this.status = status;
        this.type = type;
        this.properties = null;
    }

    public AppException(
        HttpStatus status,
        String type,
        String message,
        Map<String, Object> properties
    ) {
        super(message);
        this.status = status;
        this.type = type;
        this.properties = properties;
    }
}
