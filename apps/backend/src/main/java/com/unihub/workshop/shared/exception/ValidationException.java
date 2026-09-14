package com.unihub.workshop.shared.exception;

import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;

public class ValidationException extends AppException {

    public ValidationException(String message) {
        super(
            HttpStatus.UNPROCESSABLE_CONTENT,
            "urn:unihub:error:validation",
            message,
            Map.of(
                "validationErrors",
                List.of(message != null ? message : "Validation failed.")
            )
        );
    }

    public ValidationException(String field, String message) {
        super(
            HttpStatus.UNPROCESSABLE_CONTENT,
            "urn:unihub:error:validation",
            message,
            Map.of(
                "validationErrors",
                List.of((field != null ? field + ": " : "") + message)
            )
        );
    }

    public ValidationException(String message, List<String> validationErrors) {
        super(
            HttpStatus.UNPROCESSABLE_CONTENT,
            "urn:unihub:error:validation",
            message,
            Map.of(
                "validationErrors",
                validationErrors != null ? validationErrors : List.of()
            )
        );
    }

    public ValidationException(
        String type,
        String message,
        List<String> validationErrors
    ) {
        super(
            HttpStatus.UNPROCESSABLE_CONTENT,
            type,
            message,
            Map.of(
                "validationErrors",
                validationErrors != null ? validationErrors : List.of()
            )
        );
    }
}
