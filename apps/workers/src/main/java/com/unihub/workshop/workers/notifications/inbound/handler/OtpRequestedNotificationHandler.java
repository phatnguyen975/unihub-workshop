package com.unihub.workshop.workers.notifications.inbound.handler;

import com.unihub.workshop.workers.notifications.core.ports.in.DirectEmailDeliveryUseCase;
import com.unihub.workshop.workers.notifications.core.ports.in.NotificationEventHandler;
import com.unihub.workshop.workers.notifications.inbound.dto.OtpRequestedEventDto;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OtpRequestedNotificationHandler
    implements NotificationEventHandler<OtpRequestedEventDto>
{

    private final DirectEmailDeliveryUseCase emailDeliveryUseCase;

    @Override
    public String supportedEventType() {
        return "OtpRequestedEvent";
    }

    @Override
    public Class<OtpRequestedEventDto> eventDtoClass() {
        return OtpRequestedEventDto.class;
    }

    @Override
    public void handle(OtpRequestedEventDto event) {
        if (event == null || event.email() == null || event.otp() == null) {
            log.warn(
                "Skipping invalid OtpRequestedEvent: missing email or otp"
            );
            return;
        }

        String templateName;
        String subject;

        if (
            OtpRequestedEventDto.PURPOSE_PASSWORD_RESET.equalsIgnoreCase(
                event.purpose()
            )
        ) {
            templateName = "otp-password-reset";
            subject = "UniHub Workshop - Password Reset Verification Code";
        } else {
            templateName = "otp-activation";
            subject = "UniHub Workshop - Account Activation Verification Code";
        }

        Map<String, Object> model = Map.of(
            "otp",
            event.otp(),
            "email",
            event.email()
        );

        emailDeliveryUseCase.deliverEmail(
            event.userId(),
            event.email(),
            subject,
            templateName,
            model,
            event.idempotencyKey()
        );
    }
}
