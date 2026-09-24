package com.unihub.workshop.shared.web;

import com.unihub.workshop.shared.exception.AppException;
import com.unihub.workshop.shared.exception.ServiceUnavailableException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @Value("${app.http.retry-after-seconds:30}")
    private int defaultRetryAfterSeconds = 30;

    @Value(
        "${resilience4j.circuitbreaker.configs.default.wait-duration-in-open-state:10s}"
    )
    private Duration circuitBreakerWaitDuration = Duration.ofSeconds(10);

    private final CircuitBreakerRegistry circuitBreakerRegistry;

    public GlobalExceptionHandler() {
        this(CircuitBreakerRegistry.ofDefaults());
    }

    public GlobalExceptionHandler(
        CircuitBreakerRegistry circuitBreakerRegistry
    ) {
        this.circuitBreakerRegistry = circuitBreakerRegistry;
    }

    public static GlobalExceptionHandler forTesting() {
        return new GlobalExceptionHandler(CircuitBreakerRegistry.ofDefaults());
    }

    void setDefaultRetryAfterSeconds(int defaultRetryAfterSeconds) {
        this.defaultRetryAfterSeconds = defaultRetryAfterSeconds;
    }

    void setCircuitBreakerWaitDuration(Duration circuitBreakerWaitDuration) {
        this.circuitBreakerWaitDuration = circuitBreakerWaitDuration;
    }

    @ExceptionHandler(AppException.class)
    public ResponseEntity<ProblemDetail> handleAppException(
        AppException ex,
        HttpServletRequest request
    ) {
        ProblemDetail problem = createProblemDetail(
            ex.getStatus(),
            ex.getType(),
            ex.getStatus().getReasonPhrase(),
            ex.getMessage(),
            request.getRequestURI()
        );

        if (ex.getProperties() != null) {
            ex.getProperties().forEach(problem::setProperty);
        }

        if (
            ex.getStatus() == HttpStatus.UNPROCESSABLE_CONTENT &&
            (problem.getProperties() == null ||
                !problem.getProperties().containsKey("validationErrors"))
        ) {
            problem.setProperty(
                "validationErrors",
                List.of(
                    ex.getMessage() != null
                        ? ex.getMessage()
                        : "Validation failed."
                )
            );
        }

        ResponseEntity.BodyBuilder builder = ResponseEntity.status(
            ex.getStatus()
        );

        if (ex instanceof ServiceUnavailableException sue) {
            int retryAfter =
                sue.getRetryAfterSeconds() != null
                    ? sue.getRetryAfterSeconds()
                    : defaultRetryAfterSeconds;
            builder.header(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfter));
        }

        return builder.body(problem);
    }

    @ExceptionHandler(CallNotPermittedException.class)
    public ResponseEntity<ProblemDetail> handleCallNotPermittedException(
        CallNotPermittedException ex,
        HttpServletRequest request
    ) {
        log.warn("Circuit breaker blocked call: {}", ex.getMessage());
        ProblemDetail problem = createProblemDetail(
            HttpStatus.SERVICE_UNAVAILABLE,
            "urn:unihub:error:service-unavailable",
            "Service Unavailable",
            "The requested service is temporarily unavailable due to high failure rates. Please try again later.",
            request.getRequestURI()
        );

        long retryAfter =
            circuitBreakerWaitDuration != null
                ? circuitBreakerWaitDuration.toSeconds()
                : defaultRetryAfterSeconds;

        if (
            circuitBreakerRegistry != null &&
            ex.getCausingCircuitBreakerName() != null
        ) {
            retryAfter = circuitBreakerRegistry
                .find(ex.getCausingCircuitBreakerName())
                .map(cb -> {
                    var intervalFn = cb
                        .getCircuitBreakerConfig()
                        .getWaitIntervalFunctionInOpenState();
                    return intervalFn != null
                        ? intervalFn.apply(1) / 1000
                        : null;
                })
                .or(() ->
                    circuitBreakerRegistry
                        .getConfiguration(ex.getCausingCircuitBreakerName())
                        .map(cfg -> {
                            var intervalFn =
                                cfg.getWaitIntervalFunctionInOpenState();
                            return intervalFn != null
                                ? intervalFn.apply(1) / 1000
                                : null;
                        })
                )
                .filter(java.util.Objects::nonNull)
                .orElse(retryAfter);
        }

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .header(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfter))
            .body(problem);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
        MethodArgumentNotValidException ex,
        HttpHeaders headers,
        HttpStatusCode status,
        WebRequest request
    ) {
        String instance = ((ServletWebRequest) request)
            .getRequest()
            .getRequestURI();
        String detail = String.format(
            "Validation failed for '%s'. %d error(s) found.",
            ex.getBindingResult().getObjectName(),
            ex.getBindingResult().getErrorCount()
        );

        ProblemDetail problem = createProblemDetail(
            HttpStatus.UNPROCESSABLE_CONTENT,
            "urn:unihub:error:validation",
            "Unprocessable Content",
            detail,
            instance
        );

        List<String> validationErrors = ex
            .getBindingResult()
            .getFieldErrors()
            .stream()
            .map(error -> error.getField() + ": " + error.getDefaultMessage())
            .collect(Collectors.toList());
        problem.setProperty("validationErrors", validationErrors);

        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT).body(
            problem
        );
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ProblemDetail> handleConstraintViolationException(
        ConstraintViolationException ex,
        HttpServletRequest request
    ) {
        String instance = request.getRequestURI();
        String detail = String.format(
            "Validation failed. %d error(s) found.",
            ex.getConstraintViolations().size()
        );

        ProblemDetail problem = createProblemDetail(
            HttpStatus.UNPROCESSABLE_CONTENT,
            "urn:unihub:error:validation",
            "Unprocessable Content",
            detail,
            instance
        );

        List<String> validationErrors = ex
            .getConstraintViolations()
            .stream()
            .map(
                violation ->
                    violation.getPropertyPath().toString() +
                    ": " +
                    violation.getMessage()
            )
            .collect(Collectors.toList());
        problem.setProperty("validationErrors", validationErrors);

        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT).body(
            problem
        );
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
        org.springframework.http.converter.HttpMessageNotReadableException ex,
        HttpHeaders headers,
        HttpStatusCode status,
        WebRequest request
    ) {
        if (
            ex.getCause() instanceof
                com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException upe
        ) {
            String instance = ((ServletWebRequest) request)
                .getRequest()
                .getRequestURI();
            String fieldName = upe.getPropertyName();
            ProblemDetail problem = createProblemDetail(
                HttpStatus.UNPROCESSABLE_CONTENT,
                "urn:unihub:error:validation",
                "Unprocessable Content",
                "Request contains unrecognized or immutable field: " +
                    fieldName,
                instance
            );
            problem.setProperty(
                "validationErrors",
                List.of(fieldName + ": Field is immutable or unrecognized")
            );
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT).body(
                problem
            );
        }
        return super.handleHttpMessageNotReadable(ex, headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
        Exception ex,
        Object body,
        HttpHeaders headers,
        HttpStatusCode status,
        WebRequest request
    ) {
        String instance = ((ServletWebRequest) request)
            .getRequest()
            .getRequestURI();

        String type = "urn:unihub:error:internal";
        HttpStatus httpStatus = HttpStatus.valueOf(status.value());

        if (httpStatus == HttpStatus.BAD_REQUEST) type =
            "urn:unihub:error:bad-request";
        else if (httpStatus == HttpStatus.NOT_FOUND) type =
            "urn:unihub:error:not-found";
        else if (httpStatus == HttpStatus.FORBIDDEN) type =
            "urn:unihub:error:forbidden";
        else if (httpStatus == HttpStatus.UNAUTHORIZED) type =
            "urn:unihub:error:unauthorized";
        else if (httpStatus == HttpStatus.METHOD_NOT_ALLOWED) type =
            "urn:unihub:error:method-not-allowed";
        else if (httpStatus == HttpStatus.CONFLICT) type =
            "urn:unihub:error:conflict";
        else if (httpStatus == HttpStatus.UNPROCESSABLE_CONTENT) type =
            "urn:unihub:error:validation";
        else if (httpStatus == HttpStatus.TOO_MANY_REQUESTS) type =
            "urn:unihub:error:rate-limited";
        else if (httpStatus == HttpStatus.SERVICE_UNAVAILABLE) type =
            "urn:unihub:error:service-unavailable";
        else if (httpStatus == HttpStatus.UNSUPPORTED_MEDIA_TYPE) type =
            "urn:unihub:error:unsupported-media-type";

        ProblemDetail problem = createProblemDetail(
            httpStatus,
            type,
            httpStatus.getReasonPhrase(),
            ex.getMessage() != null
                ? ex.getMessage()
                : "An unexpected error occurred.",
            instance
        );

        if (httpStatus == HttpStatus.UNPROCESSABLE_CONTENT) {
            problem.setProperty(
                "validationErrors",
                List.of(
                    ex.getMessage() != null
                        ? ex.getMessage()
                        : "Validation failed."
                )
            );
        }

        HttpHeaders responseHeaders =
            headers != null ? new HttpHeaders(headers) : new HttpHeaders();
        if (
            httpStatus == HttpStatus.SERVICE_UNAVAILABLE &&
            !responseHeaders.containsHeader(HttpHeaders.RETRY_AFTER)
        ) {
            responseHeaders.set(
                HttpHeaders.RETRY_AFTER,
                String.valueOf(defaultRetryAfterSeconds)
            );
        }

        return ResponseEntity.status(status)
            .headers(responseHeaders)
            .body(problem);
    }

    @ExceptionHandler({
        IllegalStateException.class,
        IllegalArgumentException.class,
    })
    public ResponseEntity<ProblemDetail> handleDomainStateExceptions(
        RuntimeException ex,
        HttpServletRequest request
    ) {
        ProblemDetail problem = createProblemDetail(
            HttpStatus.UNPROCESSABLE_CONTENT,
            "urn:unihub:error:validation",
            "Unprocessable Content",
            ex.getMessage() != null
                ? ex.getMessage()
                : "State or argument is invalid.",
            request.getRequestURI()
        );
        problem.setProperty(
            "validationErrors",
            List.of(
                ex.getMessage() != null
                    ? ex.getMessage()
                    : "State or argument is invalid."
            )
        );
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT).body(
            problem
        );
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ProblemDetail> handleDataIntegrityViolation(
        DataIntegrityViolationException ex,
        HttpServletRequest request
    ) {
        ProblemDetail problem = createProblemDetail(
            HttpStatus.CONFLICT,
            "urn:unihub:error:conflict",
            "Conflict",
            "A data conflict or unique constraint violation occurred.",
            request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> handleAccessDenied(
        AccessDeniedException ex,
        HttpServletRequest request
    ) {
        ProblemDetail problem = createProblemDetail(
            HttpStatus.FORBIDDEN,
            "urn:unihub:error:forbidden",
            "Forbidden",
            "You do not have permission to access this resource.",
            request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(problem);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ProblemDetail> handleAuthenticationException(
        AuthenticationException ex,
        HttpServletRequest request
    ) {
        ProblemDetail problem = createProblemDetail(
            HttpStatus.UNAUTHORIZED,
            "urn:unihub:error:unauthorized",
            "Unauthorized",
            "Authentication is required to access this resource.",
            request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(problem);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleAllExceptions(
        Exception ex,
        HttpServletRequest request
    ) {
        log.error("Unhandled system error", ex);
        ProblemDetail problem = createProblemDetail(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "urn:unihub:error:internal",
            "Internal Server Error",
            "An unexpected system error occurred.",
            request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
            problem
        );
    }

    private ProblemDetail createProblemDetail(
        HttpStatus status,
        String type,
        String title,
        String detail,
        String instance
    ) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
            status,
            detail
        );
        problem.setType(URI.create(type));
        problem.setTitle(title);
        problem.setInstance(URI.create(instance));
        return problem;
    }
}
