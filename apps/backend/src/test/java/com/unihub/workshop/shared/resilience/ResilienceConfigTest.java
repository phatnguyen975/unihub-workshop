package com.unihub.workshop.shared.resilience;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = {
        "spring.flyway.enabled=false",
        "spring.data.redis.repositories.enabled=false",
        "datasource.primary.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1",
        "datasource.replica.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1",
        "datasource.primary.driver-class-name=org.h2.Driver",
        "datasource.replica.driver-class-name=org.h2.Driver"
})
@ActiveProfiles("test")
class ResilienceConfigTest {

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @Autowired
    private MeterRegistry meterRegistry;

    @Test
    void shouldRegisterDefaultCircuitBreakerWithExpectedThresholds() {
        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("test-cb");

        CircuitBreakerConfig config = cb.getCircuitBreakerConfig();

        assertThat(config.getFailureRateThreshold()).isEqualTo(50.0f);
        assertThat(config.getSlowCallRateThreshold()).isEqualTo(50.0f);
        assertThat(config.getSlowCallDurationThreshold()).isEqualTo(Duration.ofSeconds(2));
        assertThat(config.getMinimumNumberOfCalls()).isEqualTo(5);
        assertThat(config.getSlidingWindowSize()).isEqualTo(10);
        assertThat(config.getPermittedNumberOfCallsInHalfOpenState()).isEqualTo(3);
        assertThat(config.getSlidingWindowType()).isEqualTo(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED);
    }

    @Test
    void shouldExposeCircuitBreakerStateToMicrometer() {
        // Ensure meters are registered
        assertThat(meterRegistry.find("resilience4j.circuitbreaker.state").gauge()).isNotNull();
    }

    @Test
    void shouldTransitionStateWhenFailureThresholdBreached() {
        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("transition-test-cb");

        // Should start CLOSED
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.CLOSED);

        // Record minimum number of calls + enough failures to breach 50%
        for (int i = 0; i < 5; i++) {
            cb.onError(0, TimeUnit.SECONDS, new RuntimeException("Test Error"));
        }

        // Should transition to OPEN
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);
    }
}
