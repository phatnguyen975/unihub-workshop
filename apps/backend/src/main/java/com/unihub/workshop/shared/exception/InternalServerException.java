package com.unihub.workshop.shared.exception;

import org.springframework.http.HttpStatus;

public class InternalServerException extends AppException {

    public InternalServerException(String message) {
        super(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "urn:unihub:error:internal",
            message
        );
    }

    public InternalServerException(String type, String message) {
        super(HttpStatus.INTERNAL_SERVER_ERROR, type, message);
    }

    public InternalServerException(String message, Throwable cause) {
        super(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "urn:unihub:error:internal",
            message,
            cause
        );
    }
}
