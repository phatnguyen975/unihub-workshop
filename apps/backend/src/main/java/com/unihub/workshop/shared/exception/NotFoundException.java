package com.unihub.workshop.shared.exception;

import org.springframework.http.HttpStatus;

public class NotFoundException extends AppException {
    public NotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, "urn:unihub:error:not-found", message);
    }

    public NotFoundException(String type, String message) {
        super(HttpStatus.NOT_FOUND, type, message);
    }
}
