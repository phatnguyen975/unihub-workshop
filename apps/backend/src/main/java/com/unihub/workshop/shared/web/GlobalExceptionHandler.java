package com.unihub.workshop.shared.web;

import com.unihub.workshop.shared.exception.AppException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.net.URI;
import java.util.List;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(AppException.class)
    public ResponseEntity<ProblemDetail> handleAppException(AppException ex, HttpServletRequest request) {
        ProblemDetail problem = createProblemDetail(
                ex.getStatus(),
                ex.getType(),
                ex.getStatus().getReasonPhrase(),
                ex.getMessage(),
                request.getRequestURI());

        if (ex.getProperties() != null) {
            ex.getProperties().forEach(problem::setProperty);
        }

        return ResponseEntity.status(ex.getStatus()).body(problem);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        String instance = ((ServletWebRequest) request).getRequest().getRequestURI();
        String detail = String.format("Validation failed for '%s'. %d error(s) found.",
                ex.getBindingResult().getObjectName(),
                ex.getBindingResult().getErrorCount());

        ProblemDetail problem = createProblemDetail(
                HttpStatus.UNPROCESSABLE_CONTENT,
                "urn:unihub:error:validation",
                "Unprocessable Entity",
                detail,
                instance);

        List<String> validationErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.toList());
        problem.setProperty("validationErrors", validationErrors);

        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT).body(problem);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ProblemDetail> handleConstraintViolationException(
            ConstraintViolationException ex, HttpServletRequest request) {
        String instance = request.getRequestURI();
        String detail = String.format("Validation failed. %d error(s) found.", ex.getConstraintViolations().size());

        ProblemDetail problem = createProblemDetail(
                HttpStatus.UNPROCESSABLE_CONTENT,
                "urn:unihub:error:validation",
                "Unprocessable Entity",
                detail,
                instance);

        List<String> validationErrors = ex.getConstraintViolations().stream()
                .map(violation -> violation.getPropertyPath().toString() + ": " + violation.getMessage())
                .collect(Collectors.toList());
        problem.setProperty("validationErrors", validationErrors);

        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT).body(problem);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, Object body, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        String instance = ((ServletWebRequest) request).getRequest().getRequestURI();

        String type = "urn:unihub:error:internal";
        HttpStatus httpStatus = HttpStatus.valueOf(status.value());

        if (httpStatus == HttpStatus.BAD_REQUEST)
            type = "urn:unihub:error:validation";
        else if (httpStatus == HttpStatus.NOT_FOUND)
            type = "urn:unihub:error:not-found";
        else if (httpStatus == HttpStatus.FORBIDDEN)
            type = "urn:unihub:error:forbidden";
        else if (httpStatus == HttpStatus.UNAUTHORIZED)
            type = "urn:unihub:error:unauthorized";
        else if (httpStatus == HttpStatus.TOO_MANY_REQUESTS)
            type = "urn:unihub:error:rate-limited";

        ProblemDetail problem = createProblemDetail(
                httpStatus,
                type,
                httpStatus.getReasonPhrase(),
                ex.getMessage() != null ? ex.getMessage() : "An unexpected error occurred.",
                instance);
        return ResponseEntity.status(status).body(problem);
    }

    @ExceptionHandler({ IllegalStateException.class, IllegalArgumentException.class })
    public ResponseEntity<ProblemDetail> handleDomainStateExceptions(RuntimeException ex,
            HttpServletRequest request) {
        ProblemDetail problem = createProblemDetail(
                HttpStatus.UNPROCESSABLE_CONTENT,
                "urn:unihub:error:state",
                "Unprocessable Entity",
                ex.getMessage() != null ? ex.getMessage() : "State or argument is invalid.",
                request.getRequestURI());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT).body(problem);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        ProblemDetail problem = createProblemDetail(
                HttpStatus.FORBIDDEN,
                "urn:unihub:error:forbidden",
                "Forbidden",
                "You do not have permission to access this resource.",
                request.getRequestURI());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(problem);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ProblemDetail> handleAuthenticationException(AuthenticationException ex,
            HttpServletRequest request) {
        ProblemDetail problem = createProblemDetail(
                HttpStatus.UNAUTHORIZED,
                "urn:unihub:error:unauthorized",
                "Unauthorized",
                "Authentication is required to access this resource.",
                request.getRequestURI());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(problem);
    }

    @Override
    protected ResponseEntity<Object> handleNoHandlerFoundException(
            NoHandlerFoundException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        String instance = ((ServletWebRequest) request).getRequest().getRequestURI();
        ProblemDetail problem = createProblemDetail(
                HttpStatus.NOT_FOUND,
                "urn:unihub:error:not-found",
                "Not Found",
                "Resource not found.",
                instance);
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem);
    }

    @Override
    protected ResponseEntity<Object> handleHttpRequestMethodNotSupported(
            HttpRequestMethodNotSupportedException ex, HttpHeaders headers, HttpStatusCode status,
            WebRequest request) {
        String instance = ((ServletWebRequest) request).getRequest().getRequestURI();
        ProblemDetail problem = createProblemDetail(
                HttpStatus.METHOD_NOT_ALLOWED,
                "urn:unihub:error:method-not-allowed",
                "Method Not Allowed",
                "The requested method is not allowed for this resource.",
                instance);
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(problem);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleAllExceptions(Exception ex, HttpServletRequest request) {
        ProblemDetail problem = createProblemDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "urn:unihub:error:internal",
                "Internal Server Error",
                "An unexpected system error occurred.",
                request.getRequestURI());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problem);
    }

    private ProblemDetail createProblemDetail(HttpStatus status, String type, String title, String detail,
            String instance) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create(type));
        problem.setTitle(title);
        problem.setInstance(URI.create(instance));
        return problem;
    }
}
