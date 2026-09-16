package com.unihub.workshop.shared.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.unihub.workshop.shared.exception.InternalServerException;
import com.unihub.workshop.shared.exception.ServiceUnavailableException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

class GlobalExceptionHandlerTest {

    @RestController
    static class TestFaultController {

        @GetMapping("/test/service-unavailable-default")
        public void serviceUnavailableDefault() {
            throw new ServiceUnavailableException("Redis cluster unreachable");
        }

        @GetMapping("/test/service-unavailable-custom")
        public void serviceUnavailableCustom() {
            throw new ServiceUnavailableException(
                "External provider rate limited",
                45
            );
        }

        @GetMapping("/test/circuit-breaker-open")
        public void circuitBreakerOpen() {
            CircuitBreaker cb = CircuitBreaker.ofDefaults("stripe-payment");
            throw CallNotPermittedException.createCallNotPermittedException(cb);
        }

        @GetMapping("/test/conflict-data-integrity")
        public void conflictDataIntegrity() {
            throw new DataIntegrityViolationException(
                "duplicate key violates unique constraint"
            );
        }

        @GetMapping("/test/domain-state-invalid")
        public void domainStateInvalid() {
            throw new IllegalStateException("Account is already activated");
        }

        @GetMapping("/test/internal-server-error")
        public void internalServerError() {
            throw new InternalServerException(
                "Failed to process verification code"
            );
        }

        @GetMapping("/test/access-denied")
        public void accessDenied() {
            throw new AccessDeniedException("Forbidden action");
        }

        @GetMapping("/test/unauthorized")
        public void unauthorized() {
            throw new BadCredentialsException("Invalid credentials");
        }

        @GetMapping("/test/unexpected-exception")
        public void unexpectedException() {
            throw new RuntimeException("Unexpected error");
        }

        @PostMapping(
            value = "/test/json-only",
            consumes = MediaType.APPLICATION_JSON_VALUE
        )
        public void jsonOnly(@RequestBody String body) {}
    }

    private MockMvc mockMvc;
    private GlobalExceptionHandler exceptionHandler;

    @BeforeEach
    void setUp() {
        exceptionHandler = new GlobalExceptionHandler(null);
        mockMvc = MockMvcBuilders.standaloneSetup(new TestFaultController())
            .setControllerAdvice(
                exceptionHandler,
                new GlobalResponseBodyAdvice()
            )
            .build();
    }

    @Test
    void shouldReturnDefaultRetryAfterWhenNotSpecifiedInServiceUnavailableException()
        throws Exception {
        mockMvc
            .perform(
                get("/test/service-unavailable-default").accept(
                    MediaType.APPLICATION_JSON
                )
            )
            .andExpect(status().isServiceUnavailable())
            .andExpect(header().string("Retry-After", "30"))
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(
                jsonPath("$.error.type").value(
                    "urn:unihub:error:service-unavailable"
                )
            )
            .andExpect(
                jsonPath("$.error.detail").value("Redis cluster unreachable")
            );
    }

    @Test
    void shouldReturnCustomRetryAfterWhenSpecifiedInServiceUnavailableException()
        throws Exception {
        mockMvc
            .perform(
                get("/test/service-unavailable-custom").accept(
                    MediaType.APPLICATION_JSON
                )
            )
            .andExpect(status().isServiceUnavailable())
            .andExpect(header().string("Retry-After", "45"))
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(
                jsonPath("$.error.type").value(
                    "urn:unihub:error:service-unavailable"
                )
            )
            .andExpect(
                jsonPath("$.error.detail").value(
                    "External provider rate limited"
                )
            );
    }

    @Test
    void shouldReturnCircuitBreakerWaitDurationRetryAfterOnCallNotPermittedException()
        throws Exception {
        exceptionHandler.setCircuitBreakerWaitDuration(Duration.ofSeconds(15));

        mockMvc
            .perform(
                get("/test/circuit-breaker-open").accept(
                    MediaType.APPLICATION_JSON
                )
            )
            .andExpect(status().isServiceUnavailable())
            .andExpect(header().string("Retry-After", "15"))
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(
                jsonPath("$.error.type").value(
                    "urn:unihub:error:service-unavailable"
                )
            )
            .andExpect(jsonPath("$.error.title").value("Service Unavailable"));
    }

    @Test
    void shouldRespectConfiguredDefaultRetryAfterWhenModified()
        throws Exception {
        exceptionHandler.setDefaultRetryAfterSeconds(60);

        mockMvc
            .perform(
                get("/test/service-unavailable-default").accept(
                    MediaType.APPLICATION_JSON
                )
            )
            .andExpect(status().isServiceUnavailable())
            .andExpect(header().string("Retry-After", "60"));
    }

    @Test
    void shouldReturnDynamicCircuitBreakerWaitDurationWhenRegistryIsConfigured()
        throws Exception {
        CircuitBreakerConfig customConfig = CircuitBreakerConfig.custom()
            .waitDurationInOpenState(Duration.ofSeconds(25))
            .build();
        CircuitBreakerRegistry registry = CircuitBreakerRegistry.ofDefaults();
        registry.circuitBreaker("stripe-payment", customConfig);
        GlobalExceptionHandler customHandler = new GlobalExceptionHandler(
            registry
        );
        MockMvc customMockMvc = MockMvcBuilders.standaloneSetup(
            new TestFaultController()
        )
            .setControllerAdvice(customHandler, new GlobalResponseBodyAdvice())
            .build();

        customMockMvc
            .perform(
                get("/test/circuit-breaker-open").accept(
                    MediaType.APPLICATION_JSON
                )
            )
            .andExpect(status().isServiceUnavailable())
            .andExpect(header().string("Retry-After", "25"))
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(
                jsonPath("$.error.type").value(
                    "urn:unihub:error:service-unavailable"
                )
            );
    }

    @Test
    void shouldReturnConflictOnDataIntegrityViolationException()
        throws Exception {
        mockMvc
            .perform(
                get("/test/conflict-data-integrity").accept(
                    MediaType.APPLICATION_JSON
                )
            )
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:conflict")
            )
            .andExpect(jsonPath("$.error.title").value("Conflict"))
            .andExpect(
                jsonPath("$.error.detail").value(
                    "A data conflict or unique constraint violation occurred."
                )
            );
    }

    @Test
    void shouldReturnUnprocessableContentOnDomainStateException()
        throws Exception {
        mockMvc
            .perform(
                get("/test/domain-state-invalid").accept(
                    MediaType.APPLICATION_JSON
                )
            )
            .andExpect(status().isUnprocessableContent())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:validation")
            )
            .andExpect(
                jsonPath("$.error.validationErrors[0]").value(
                    "Account is already activated"
                )
            );
    }

    @Test
    void shouldReturnInternalServerErrorOnInternalServerException()
        throws Exception {
        mockMvc
            .perform(
                get("/test/internal-server-error").accept(
                    MediaType.APPLICATION_JSON
                )
            )
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:internal")
            )
            .andExpect(
                jsonPath("$.error.detail").value(
                    "Failed to process verification code"
                )
            );
    }

    @Test
    void shouldReturnForbiddenOnAccessDeniedException() throws Exception {
        mockMvc
            .perform(
                get("/test/access-denied").accept(MediaType.APPLICATION_JSON)
            )
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:forbidden")
            );
    }

    @Test
    void shouldReturnUnauthorizedOnAuthenticationException() throws Exception {
        mockMvc
            .perform(
                get("/test/unauthorized").accept(MediaType.APPLICATION_JSON)
            )
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:unauthorized")
            );
    }

    @Test
    void shouldReturnInternalServerErrorOnUnhandledException()
        throws Exception {
        mockMvc
            .perform(
                get("/test/unexpected-exception").accept(
                    MediaType.APPLICATION_JSON
                )
            )
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(
                jsonPath("$.error.type").value("urn:unihub:error:internal")
            )
            .andExpect(
                jsonPath("$.error.detail").value(
                    "An unexpected system error occurred."
                )
            );
    }

    @Test
    void shouldReturnUnsupportedMediaTypeOnWrongContentType() throws Exception {
        mockMvc
            .perform(
                post("/test/json-only")
                    .contentType(MediaType.TEXT_PLAIN)
                    .content("raw text body")
            )
            .andExpect(status().isUnsupportedMediaType())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(
                jsonPath("$.error.type").value(
                    "urn:unihub:error:unsupported-media-type"
                )
            );
    }
}
