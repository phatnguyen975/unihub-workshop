package com.unihub.workshop.shared.exception;

import org.springframework.http.HttpStatus;

public class InvalidOtpException extends AppException {

    public static final String DEFAULT_MESSAGE = "Invalid or expired activation code.";

    public InvalidOtpException() {
        this(DEFAULT_MESSAGE);
    }

    public InvalidOtpException(String message) {
        super(HttpStatus.UNPROCESSABLE_CONTENT, "urn:unihub:error:otp-invalid", message);
    }
}
