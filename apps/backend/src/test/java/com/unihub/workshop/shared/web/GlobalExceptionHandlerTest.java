package com.unihub.workshop.shared.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.unihub.workshop.shared.exception.ServiceUnavailableException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
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
}
