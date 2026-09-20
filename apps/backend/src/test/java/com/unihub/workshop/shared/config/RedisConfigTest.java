package com.unihub.workshop.shared.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.lettuce.core.ClientOptions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.data.redis.autoconfigure.LettuceClientOptionsBuilderCustomizer;

class RedisConfigTest {

    @Test
    void lettuceClientOptionsBuilderCustomizer_configuresFailFastOptions() {
        // Arrange
        RedisConfig redisConfig = new RedisConfig();
        LettuceClientOptionsBuilderCustomizer customizer =
            redisConfig.lettuceClientOptionsBuilderCustomizer();
        ClientOptions.Builder builder = ClientOptions.builder();

        // Act
        customizer.customize(builder);
        ClientOptions options = builder.build();

        // Assert
        assertThat(options.getDisconnectedBehavior()).isEqualTo(
            ClientOptions.DisconnectedBehavior.REJECT_COMMANDS
        );
        assertThat(options.getTimeoutOptions().isTimeoutCommands()).isTrue();
        assertThat(
            options.getTimeoutOptions().isApplyConnectionTimeout()
        ).isTrue();
    }
}
