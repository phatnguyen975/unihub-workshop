package com.unihub.workshop.shared.config;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.TimeoutOptions;
import org.springframework.boot.data.redis.autoconfigure.LettuceClientOptionsBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Redis client infrastructure configuration.
 * <p>
 * Enforces fail-closed, fail-fast resilience semantics:
 * <ul>
 *   <li>{@link ClientOptions.DisconnectedBehavior#REJECT_COMMANDS}: Rejects incoming commands
 *       immediately with a RedisException when disconnected from Redis, rather than queueing
 *       them in an unbound memory buffer. This ensures exception catch blocks activate in &lt;1ms
 *       to return 503 Service Unavailable without hanging HTTP request threads.</li>
 *   <li>{@link TimeoutOptions#enabled()}: Enforces command timeout on in-flight or queued commands,
 *       automatically aligned with {@code spring.data.redis.timeout} configured on the connection.</li>
 * </ul>
 */
@Configuration
public class RedisConfig {

    @Bean
    public LettuceClientOptionsBuilderCustomizer lettuceClientOptionsBuilderCustomizer() {
        return builder ->
            builder
                .disconnectedBehavior(
                    ClientOptions.DisconnectedBehavior.REJECT_COMMANDS
                )
                .timeoutOptions(TimeoutOptions.enabled());
    }
}
