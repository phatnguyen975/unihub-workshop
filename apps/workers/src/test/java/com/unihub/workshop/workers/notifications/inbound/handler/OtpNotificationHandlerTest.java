package com.unihub.workshop.workers.notifications.inbound.handler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.unihub.workshop.workers.notifications.core.models.DirectEmailDeliveryCommand;
import com.unihub.workshop.workers.notifications.core.ports.in.DirectEmailDeliveryUseCase;
import com.unihub.workshop.workers.notifications.inbound.dto.OtpRequestedEventDto;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OtpNotificationHandlerTest {

    @Mock
    private DirectEmailDeliveryUseCase emailDeliveryUseCase;

    private OtpRequestedNotificationHandler handler;

    @BeforeEach
    void setUp() {
        handler = new OtpRequestedNotificationHandler(emailDeliveryUseCase);
    }

    @Test
    @DisplayName(
        "Should deliver activation email template when purpose is ACTIVATION"
    )
    void shouldDeliverActivationEmail() {
        UUID userId = UUID.randomUUID();
        String requestedAt = "2026-10-07T03:00:00Z";
        OtpRequestedEventDto dto = new OtpRequestedEventDto(
            userId,
            "student@hcmus.edu.vn",
            "123456",
            OtpRequestedEventDto.PURPOSE_ACTIVATION,
            requestedAt
        );

        handler.handle(dto);

        verify(emailDeliveryUseCase).deliverEmail(
            DirectEmailDeliveryCommand.builder()
                .userId(userId)
                .recipientAddress("student@hcmus.edu.vn")
                .subject(
                    "UniHub Workshop — Account Activation Verification Code"
                )
                .templateName("otp-activation")
                .templateModel(
                    Map.of("otp", "123456", "email", "student@hcmus.edu.vn")
                )
                .idempotencyKey("OTP:" + userId + ":" + requestedAt)
                .build()
        );
    }

    @Test
    @DisplayName(
        "Should deliver password reset email template when purpose is PASSWORD_RESET"
    )
    void shouldDeliverPasswordResetEmail() {
        UUID userId = UUID.randomUUID();
        String requestedAt = "2026-10-07T03:00:00Z";
        OtpRequestedEventDto dto = new OtpRequestedEventDto(
            userId,
            "student@hcmus.edu.vn",
            "654321",
            OtpRequestedEventDto.PURPOSE_PASSWORD_RESET,
            requestedAt
        );

        handler.handle(dto);

        verify(emailDeliveryUseCase).deliverEmail(
            DirectEmailDeliveryCommand.builder()
                .userId(userId)
                .recipientAddress("student@hcmus.edu.vn")
                .subject("UniHub Workshop — Password Reset Verification Code")
                .templateName("otp-password-reset")
                .templateModel(
                    Map.of("otp", "654321", "email", "student@hcmus.edu.vn")
                )
                .idempotencyKey("OTP:" + userId + ":" + requestedAt)
                .build()
        );
    }

    @Test
    @DisplayName("Should skip delivery when event is null or has missing email")
    void shouldSkipWhenInvalid() {
        handler.handle(null);
        verify(emailDeliveryUseCase, never()).deliverEmail(any());

        OtpRequestedEventDto invalidDto = new OtpRequestedEventDto(
            UUID.randomUUID(),
            null,
            "123456",
            "ACTIVATION",
            "2026-10-07T03:00:00Z"
        );
        handler.handle(invalidDto);
        verify(emailDeliveryUseCase, never()).deliverEmail(any());
    }
}
