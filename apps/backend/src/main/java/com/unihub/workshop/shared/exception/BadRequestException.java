package com.unihub.workshop.shared.exception;

import java.util.Map;
import org.springframework.http.HttpStatus;

public class BadRequestException extends AppException {

    public BadRequestException(String message) {
        super(HttpStatus.BAD_REQUEST, "urn:unihub:error:bad-request", message);
    }

    public BadRequestException(String type, String message) {
        super(HttpStatus.BAD_REQUEST, type, message);
    }

    public BadRequestException(
        String type,
        String message,
        Map<String, Object> properties
    ) {
        super(HttpStatus.BAD_REQUEST, type, message, properties);
    }
}
