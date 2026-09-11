package com.unihub.workshop.iam.domain.events;

import java.time.Instant;
import java.util.UUID;
import org.springframework.modulith.events.Externalized;

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
