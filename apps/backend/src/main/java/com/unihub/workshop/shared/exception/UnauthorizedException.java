package com.unihub.workshop.shared.exception;

import org.springframework.http.HttpStatus;

public class UnauthorizedException extends AppException {
    public UnauthorizedException(String message) {
        super(HttpStatus.UNAUTHORIZED, "urn:unihub:error:unauthorized", message);
    }

    public UnauthorizedException(String type, String message) {
        super(HttpStatus.UNAUTHORIZED, type, message);
    }
}
