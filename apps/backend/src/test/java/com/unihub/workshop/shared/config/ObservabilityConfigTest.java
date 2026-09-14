package com.unihub.workshop.shared.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ObservabilityConfig} in the backend runtime.
 */
class ObservabilityConfigTest {

    private static final String APPLICATION_NAME = "unihub-backend";

    /**
     * Builds a {@link MeterRegistry} with the specified application name and host.
     *
     * @param applicationName the name of the application
     * @param host            the host name
     * @return the configured meter registry
     */
    private MeterRegistry buildRegistryWithConfig(
        String applicationName,
        String host
    ) {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ObservabilityConfig config = new ObservabilityConfig(
            applicationName,
            host
        );
        config.configureCommonTags(registry);
        return registry;
    }

    @Test
    void shouldRegisterApplicationTag_whenRegistryIsConfigured() {
        // Arrange
        MeterRegistry registry = buildRegistryWithConfig(
            APPLICATION_NAME,
            "node-1"
        );

        // Act
        registry.counter("test.counter").increment();

        // Assert
        List<Tag> tags = registry
            .find("test.counter")
            .counter()
            .getId()
            .getTags();
        assertThat(tags).extracting(Tag::getKey).contains("application");

        String applicationTagValue = tags
            .stream()
            .filter(t -> "application".equals(t.getKey()))
            .findFirst()
            .map(Tag::getValue)
            .orElse(null);
        assertThat(applicationTagValue).isEqualTo(APPLICATION_NAME);
    }

    @Test
    void shouldRegisterHostTag_whenRegistryIsConfigured() {
        // Arrange
        String expectedHost = "node-42";
        MeterRegistry registry = buildRegistryWithConfig(
            APPLICATION_NAME,
            expectedHost
        );

        // Act
        registry.counter("test.counter").increment();

        // Assert
        List<Tag> tags = registry
            .find("test.counter")
            .counter()
            .getId()
            .getTags();
        assertThat(tags).extracting(Tag::getKey).contains("host");

        String hostTagValue = tags
            .stream()
            .filter(t -> "host".equals(t.getKey()))
            .findFirst()
            .map(Tag::getValue)
            .orElse(null);
        assertThat(hostTagValue).isEqualTo(expectedHost);
    }

    @Test
    void shouldUseConfiguredApplicationName_notHardcodedValue() {
        // Arrange
        String customName = "some-other-service";
        MeterRegistry registry = buildRegistryWithConfig(customName, "node-1");

        // Act
        registry.counter("test.counter").increment();

        // Assert
        String applicationTagValue = registry
            .find("test.counter")
            .counter()
            .getId()
            .getTags()
            .stream()
            .filter(t -> "application".equals(t.getKey()))
            .findFirst()
            .map(Tag::getValue)
            .orElse(null);
        assertThat(applicationTagValue).isEqualTo(customName);
    }

    @Test
    void shouldRegisterBothApplicationAndHostTags_onTheSameMeter() {
        // Arrange
        MeterRegistry registry = buildRegistryWithConfig(
            APPLICATION_NAME,
            "node-99"
        );

        // Act
        registry.counter("test.counter").increment();

        // Assert
        List<String> tagKeys = registry
            .find("test.counter")
            .counter()
            .getId()
            .getTags()
            .stream()
            .map(Tag::getKey)
            .toList();
        assertThat(tagKeys).contains("application", "host");
    }
}
