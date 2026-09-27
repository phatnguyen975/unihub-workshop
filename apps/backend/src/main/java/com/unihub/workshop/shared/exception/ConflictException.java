package com.unihub.workshop.shared.exception;

import org.springframework.http.HttpStatus;

public class ConflictException extends AppException {

    public ConflictException(String message) {
        super(HttpStatus.CONFLICT, "urn:unihub:error:conflict", message);
    }
}
