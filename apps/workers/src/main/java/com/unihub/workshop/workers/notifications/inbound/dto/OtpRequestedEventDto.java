package com.unihub.workshop.workers.notifications.inbound.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.unihub.workshop.workers.notifications.core.ports.in.ConsumedNotificationEvent;
import java.util.UUID;

public record OtpRequestedEventDto(
    @JsonProperty("userId") UUID userId,
    @JsonProperty("email") String email,
    @JsonProperty("otp") String otp,
    @JsonProperty("purpose") String purpose,
    @JsonProperty("requestedAt") String requestedAt
) implements ConsumedNotificationEvent {
    public static final String PURPOSE_ACTIVATION = "ACTIVATION";
    public static final String PURPOSE_PASSWORD_RESET = "PASSWORD_RESET";

    @Override
    public String eventType() {
        return "OtpRequestedEvent";
    }

    @Override
    public String idempotencyKey() {
        return "OTP:" + userId + ":" + (requestedAt != null ? requestedAt : "");
    }
}
