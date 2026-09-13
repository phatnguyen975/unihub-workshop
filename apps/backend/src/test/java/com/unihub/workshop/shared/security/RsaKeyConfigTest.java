package com.unihub.workshop.shared.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.convert.support.ConfigurableConversionService;

class RsaKeyConfigTest {

    private final ApplicationContextRunner contextRunner =
        new ApplicationContextRunner()
            .withInitializer(context ->
                context
                    .getEnvironment()
                    .setConversionService(
                        (ConfigurableConversionService) ApplicationConversionService.getSharedInstance()
                    )
            )
            .withUserConfiguration(RsaKeyConfig.class);

    @Test
    void contextLoads_withValidKeys() {
        contextRunner
            .withPropertyValues(
                "rsa.private-key=classpath:security/valid-private.pem",
                "rsa.public-key=classpath:security/valid-public.pem"
            )
            .run(context -> {
                assertThat(context).hasNotFailed();
                RsaKeyProperties properties = context.getBean(
                    RsaKeyProperties.class
                );
                assertThat(properties.privateKey()).isNotNull();
                assertThat(properties.publicKey()).isNotNull();
            });
    }

    @Test
    void contextFails_whenPropertyMissing() {
        contextRunner
            .withPropertyValues(
                "rsa.public-key=classpath:security/valid-public.pem"
            )
            .run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure()).hasStackTraceContaining(
                    "Missing public or private key"
                );
            });
    }

    @Test
    void contextFails_whenPrivateKeyFileAbsent() {
        contextRunner
            .withPropertyValues(
                "rsa.private-key=classpath:security/non-existent-private.pem",
                "rsa.public-key=classpath:security/valid-public.pem"
            )
            .run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure()).hasStackTraceContaining(
                    "Cannot read private key from"
                );
            });
    }

    @Test
    void contextFails_whenPrivateKeyMalformed() {
        contextRunner
            .withPropertyValues(
                "rsa.private-key=classpath:security/malformed-private.pem",
                "rsa.public-key=classpath:security/valid-public.pem"
            )
            .run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure()).hasStackTraceContaining(
                    "Cannot read private key from"
                );
            });
    }

    @Test
    void contextFails_whenPublicKeyFileAbsent() {
        contextRunner
            .withPropertyValues(
                "rsa.private-key=classpath:security/valid-private.pem",
                "rsa.public-key=classpath:security/non-existent-public.pem"
            )
            .run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure()).hasStackTraceContaining(
                    "Cannot read public key from"
                );
            });
    }

    @Test
    void contextFails_whenPublicKeyMalformed() {
        contextRunner
            .withPropertyValues(
                "rsa.private-key=classpath:security/valid-private.pem",
                "rsa.public-key=classpath:security/malformed-public.pem"
            )
            .run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure()).hasStackTraceContaining(
                    "Cannot read public key from"
                );
            });
    }
}
