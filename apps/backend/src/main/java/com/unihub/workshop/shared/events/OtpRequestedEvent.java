package com.unihub.workshop.shared.events;

import java.time.Instant;
import java.util.UUID;
import org.springframework.modulith.events.Externalized;

/**
 * Public integration event published when an OTP is requested.
 * Externalized to RabbitMQ queue q.notifications via routing key "notifications".
 */
@Externalized("unihub.events::notifications")
public record OtpRequestedEvent(
    UUID userId,
    String email,
    String otp,
    String purpose,
    Instant requestedAt
) {
    public static final String PURPOSE_ACTIVATION = "ACTIVATION";
    public static final String PURPOSE_PASSWORD_RESET = "PASSWORD_RESET";

    public OtpRequestedEvent(
        UUID userId,
        String email,
        String otp,
        String purpose
    ) {
        this(userId, email, otp, purpose, Instant.now());
    }
}
