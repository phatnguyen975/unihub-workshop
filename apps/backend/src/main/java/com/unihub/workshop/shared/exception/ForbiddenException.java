package com.unihub.workshop.shared.exception;

import org.springframework.http.HttpStatus;

public class ForbiddenException extends AppException {

    public ForbiddenException(String message) {
        super(HttpStatus.FORBIDDEN, "urn:unihub:error:forbidden", message);
    }

    public ForbiddenException(String type, String message) {
        super(HttpStatus.FORBIDDEN, type, message);
    }
}
