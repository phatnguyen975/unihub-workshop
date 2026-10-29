package com.unihub.workshop.registrations.application.strategy;

import com.unihub.workshop.iam.api.dto.StudentProfileDto;
import com.unihub.workshop.registrations.application.ports.out.QrSignaturePort;
import com.unihub.workshop.registrations.application.ports.out.RegistrationRepository;
import com.unihub.workshop.registrations.application.ports.out.WorkshopRegistrationBroadcaster;
import com.unihub.workshop.registrations.domain.model.QrTicket;
import com.unihub.workshop.registrations.domain.model.Registration;
import com.unihub.workshop.shared.events.WorkshopRegisteredEvent;
import com.unihub.workshop.workshops.api.dto.WorkshopRegistrationDetailsDto;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Concrete registration strategy for free workshops (price == 0).
 * Immediately generates signed QR ticket, creates CONFIRMED registration, and publishes outbox event.
 */
@Component
@RequiredArgsConstructor
public class FreeRegistrationStrategy implements RegistrationStrategy {

    private final RegistrationRepository registrationRepository;
    private final QrSignaturePort qrSignatureService;
    private final ApplicationEventPublisher eventPublisher;
    private final WorkshopRegistrationBroadcaster workshopRegistrationBroadcaster;

    @Override
    public boolean supports(WorkshopRegistrationDetailsDto workshop) {
        return (
            workshop != null &&
            workshop.price() != null &&
            BigDecimal.ZERO.compareTo(workshop.price()) == 0
        );
    }

    @Override
    @Transactional
    public Registration register(
        StudentProfileDto student,
        WorkshopRegistrationDetailsDto workshop,
        UUID idempotencyKeyId
    ) {
        UUID registrationId = UUID.randomUUID();
        Instant now = Instant.now();

        QrTicket qrTicket = qrSignatureService.generateSignedTicket(
            registrationId,
            workshop.id(),
            student.userId(),
            now
        );

        Registration registration =
            Registration.createConfirmedFreeRegistration(
                registrationId,
                workshop.id(),
                student.userId(),
                idempotencyKeyId,
                qrTicket
            );

        Registration saved = registrationRepository.save(registration);

        WorkshopRegisteredEvent event = new WorkshopRegisteredEvent(
            saved.id(),
            workshop.id(),
            student.userId(),
            student.email(),
            student.fullName(),
            workshop.title(),
            workshop.slug(),
            workshop.startsAt(),
            workshop.endsAt(),
            workshop.location(),
            qrTicket.qrPayload(),
            qrTicket.qrSignature(),
            qrTicket.qrIssuedAt(),
            now
        );
        eventPublisher.publishEvent(event);
        workshopRegistrationBroadcaster.broadcast(workshop.id());

        return saved;
    }
}
