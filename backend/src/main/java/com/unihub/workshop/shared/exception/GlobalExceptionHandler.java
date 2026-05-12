package com.unihub.workshop.shared.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.ObjectError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.unihub.workshop.shared.web.ApiResponse;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private final MetadataGenerator metadataGenerator;

    public GlobalExceptionHandler(MetadataGenerator metadataGenerator) {
        this.metadataGenerator = metadataGenerator;
    }

    @ExceptionHandler(AppException.class)
    public ResponseEntity<ApiResponse<Void>> handleAppException(
            AppException ex,
            HttpServletRequest request) {
        HttpStatus status = ex.getStatus();
        ApiResponse.ProblemDetails error = problemDetails(
                errorType(ex.getErrorCode(), "business"),
                "Business Rule Violation",
                ex.getMessage(),
                request.getRequestURI(),
                null);
        return ResponseEntity.status(status)
                .body(ApiResponse.failure(ex.getMessage(), metadata(status), error));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpServletRequest request) {
        List<String> validationErrors = collectFieldErrors(ex);
        ApiResponse.ProblemDetails error = problemDetails(
                "urn:unihub:error:validation",
                "Validation Error",
                "One or more fields failed validation.",
                request.getRequestURI(),
                validationErrors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.failure("Invalid request payload", metadata(HttpStatus.BAD_REQUEST), error));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(
            ConstraintViolationException ex,
            HttpServletRequest request) {
        List<String> validationErrors = collectConstraintViolations(ex);
        ApiResponse.ProblemDetails error = problemDetails(
                "urn:unihub:error:validation",
                "Validation Error",
                "One or more fields failed validation.",
                request.getRequestURI(),
                validationErrors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.failure("Invalid request payload", metadata(HttpStatus.BAD_REQUEST), error));
    }

    @ExceptionHandler({ NoHandlerFoundException.class, NoResourceFoundException.class })
    public ResponseEntity<ApiResponse<Void>> handleNotFound(
            Exception ex,
            HttpServletRequest request) {
        ApiResponse.ProblemDetails error = problemDetails(
                "urn:unihub:error:not_found",
                "Not Found",
                ex.getMessage(),
                request.getRequestURI(),
                null);
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.failure("Resource not found", metadata(HttpStatus.NOT_FOUND), error));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotAllowed(
            HttpRequestMethodNotSupportedException ex,
            HttpServletRequest request) {
        ApiResponse.ProblemDetails error = problemDetails(
                "urn:unihub:error:method_not_allowed",
                "Method Not Allowed",
                ex.getMessage(),
                request.getRequestURI(),
                null);
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(ApiResponse.failure("Method not allowed", metadata(HttpStatus.METHOD_NOT_ALLOWED), error));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGenericException(
            Exception ex,
            HttpServletRequest request) {
        ApiResponse.ProblemDetails error = problemDetails(
                "urn:unihub:error:internal",
                "Internal Server Error",
                "An unexpected error occurred.",
                request.getRequestURI(),
                null);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.failure("Internal server error", metadata(HttpStatus.INTERNAL_SERVER_ERROR), error));
    }

    private ApiResponse.Metadata metadata(HttpStatus status) {
        return metadataGenerator.create(status);
    }

    private ApiResponse.ProblemDetails problemDetails(
            String type,
            String title,
            String detail,
            String instance,
            List<String> validationErrors) {
        return new ApiResponse.ProblemDetails(type, title, detail, instance, validationErrors);
    }

    private List<String> collectFieldErrors(MethodArgumentNotValidException ex) {
        Set<String> errors = new LinkedHashSet<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(error -> errors.add(formatFieldError(error.getField(), error.getDefaultMessage())));
        for (ObjectError error : ex.getBindingResult().getGlobalErrors()) {
            errors.add(formatFieldError(error.getObjectName(), error.getDefaultMessage()));
        }
        return new ArrayList<>(errors);
    }

    private List<String> collectConstraintViolations(ConstraintViolationException ex) {
        Set<String> errors = new LinkedHashSet<>();
        for (ConstraintViolation<?> violation : ex.getConstraintViolations()) {
            String field = extractProperty(violation.getPropertyPath().toString());
            errors.add(formatFieldError(field, violation.getMessage()));
        }
        return new ArrayList<>(errors);
    }

    private String formatFieldError(String field, String message) {
        String safeField = (field == null || field.isBlank()) ? "request" : field;
        String safeMessage = (message == null || message.isBlank()) ? "is invalid" : message;
        return safeField + ": " + safeMessage;
    }

    private String extractProperty(String path) {
        if (path == null || path.isBlank()) {
            return "request";
        }
        int lastDot = path.lastIndexOf('.');
        if (lastDot >= 0 && lastDot < path.length() - 1) {
            return path.substring(lastDot + 1);
        }
        return path;
    }

    private String errorType(String errorCode, String fallback) {
        if (errorCode != null && !errorCode.isBlank()) {
            return "urn:unihub:error:" + errorCode;
        }
        return "urn:unihub:error:" + fallback;
    }
}
