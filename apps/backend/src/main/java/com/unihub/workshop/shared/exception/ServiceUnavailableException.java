package com.unihub.workshop.shared.exception;

import org.springframework.http.HttpStatus;
import java.util.Map;

public class ServiceUnavailableException extends AppException {

    public ServiceUnavailableException(String type, String message) {
        super(HttpStatus.SERVICE_UNAVAILABLE, type, message);
    }

    public ServiceUnavailableException(String type, String message, Map<String, Object> properties) {
        super(HttpStatus.SERVICE_UNAVAILABLE, type, message, properties);
    }
}
