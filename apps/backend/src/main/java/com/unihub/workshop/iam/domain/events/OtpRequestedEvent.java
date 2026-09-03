package com.unihub.workshop.iam.domain.events;

import org.springframework.modulith.events.Externalized;
import java.time.Instant;
import java.util.UUID;

@Externalized("unihub.events::notifications")
public record OtpRequestedEvent(
        UUID userId,
        String email,
        String otp,
        String purpose,
        Instant requestedAt) {

    public OtpRequestedEvent(UUID userId, String email, String otp, String purpose) {
        this(userId, email, otp, purpose, Instant.now());
    }
}
