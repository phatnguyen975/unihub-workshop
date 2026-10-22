package com.unihub.workshop.registrations.application.facade;

import com.unihub.workshop.iam.api.IamApi;
import com.unihub.workshop.iam.api.dto.StudentProfileDto;
import com.unihub.workshop.registrations.api.RegistrationApi;
import com.unihub.workshop.registrations.api.dto.ConfirmPaidRegistrationCommand;
import com.unihub.workshop.registrations.api.dto.FailPaidRegistrationCommand;
import com.unihub.workshop.registrations.api.dto.RegistrationConfirmedDto;
import com.unihub.workshop.registrations.api.dto.RegistrationPaymentContextDto;
import com.unihub.workshop.registrations.application.ports.out.CapacityPort;
import com.unihub.workshop.registrations.application.ports.out.QrSignaturePort;
import com.unihub.workshop.registrations.application.ports.out.RegistrationRepository;
import com.unihub.workshop.registrations.domain.model.QrTicket;
import com.unihub.workshop.registrations.domain.model.Registration;
import com.unihub.workshop.registrations.domain.model.RegistrationStatus;
import com.unihub.workshop.shared.events.WorkshopRegisteredEvent;
import com.unihub.workshop.workshops.api.WorkshopApi;
import com.unihub.workshop.workshops.api.dto.WorkshopRegistrationDetailsDto;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Public facade implementing {@link RegistrationApi}.
 * Serves synchronous cross-module calls from peer modules (e.g. payments).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RegistrationFacade implements RegistrationApi {

    private final RegistrationRepository registrationRepository;
    private final QrSignaturePort qrSignaturePort;
    private final ApplicationEventPublisher eventPublisher;
    private final IamApi iamApi;
    private final WorkshopApi workshopApi;
    private final CapacityPort capacityPort;

    @Override
    @Transactional
    public RegistrationConfirmedDto confirmPaidRegistration(
        ConfirmPaidRegistrationCommand command
    ) {
        if (command == null || command.registrationId() == null) {
            log.warn(
                "Cannot confirm paid registration: command or registrationId is null"
            );
            return null;
        }

        Optional<Registration> optRegistration =
            registrationRepository.findById(command.registrationId());
        if (optRegistration.isEmpty()) {
            log.warn(
                "Registration [{}] not found for payment confirmation. Acknowledging without state change.",
                command.registrationId()
            );
            return null;
        }

        Registration registration = optRegistration.get();
        if (registration.status() != RegistrationStatus.PENDING) {
            log.warn(
                "Registration [{}] has status [{}], expected PENDING. Acknowledging without duplicate confirmation.",
                registration.id(),
                registration.status()
            );
            return null;
        }

        Instant now = Instant.now();
        QrTicket qrTicket = qrSignaturePort.generateSignedTicket(
            registration.id(),
            registration.workshopId(),
            registration.studentUserId(),
            now
        );

        Registration confirmed = registration.confirmWithQrTicket(
            qrTicket,
            now
        );
        Registration saved = registrationRepository.save(confirmed);

        StudentProfileDto student = iamApi
            .getStudentProfile(registration.studentUserId())
            .orElse(null);
        WorkshopRegistrationDetailsDto workshop = workshopApi
            .getWorkshopForRegistration(registration.workshopId())
            .orElse(null);

        WorkshopRegisteredEvent outboxEvent = new WorkshopRegisteredEvent(
            saved.id(),
            saved.workshopId(),
            saved.studentUserId(),
            student != null ? student.email() : null,
            student != null ? student.fullName() : null,
            workshop != null ? workshop.title() : null,
            workshop != null ? workshop.slug() : null,
            workshop != null ? workshop.startsAt() : null,
            workshop != null ? workshop.endsAt() : null,
            workshop != null ? workshop.location() : null,
            qrTicket.qrPayload(),
            qrTicket.qrSignature(),
            qrTicket.qrIssuedAt(),
            now
        );

        eventPublisher.publishEvent(outboxEvent);

        log.info(
            "Successfully confirmed paid registration [{}] for workshop [{}] with payment [{}]",
            saved.id(),
            saved.workshopId(),
            command.providerPaymentId()
        );

        return new RegistrationConfirmedDto(
            saved.id(),
            saved.workshopId(),
            saved.studentUserId(),
            qrTicket.qrPayload(),
            qrTicket.qrIssuedAt()
        );
    }

    @Override
    @Transactional
    public boolean failPaidRegistration(FailPaidRegistrationCommand command) {
        if (command == null || command.registrationId() == null) {
            log.warn(
                "Cannot fail paid registration: command or registrationId is null"
            );
            return false;
        }

        Optional<Registration> optRegistration =
            registrationRepository.findById(command.registrationId());
        if (optRegistration.isEmpty()) {
            log.warn(
                "Registration [{}] not found for failure transition. Acknowledging without state change.",
                command.registrationId()
            );
            return false;
        }

        Registration registration = optRegistration.get();
        if (registration.status() != RegistrationStatus.PENDING) {
            log.warn(
                "Registration [{}] has status [{}], expected PENDING. Acknowledging without duplicate failure transition.",
                registration.id(),
                registration.status()
            );
            return false;
        }

        Instant now = Instant.now();
        Registration failed = registration.markFailed(now);
        registrationRepository.save(failed);

        registerPostCommitCapacityRelease(registration.workshopId());

        log.info(
            "Successfully marked registration [{}] as FAILED and released capacity for workshop [{}]. Reason: {}",
            registration.id(),
            registration.workshopId(),
            command.failureReason()
        );

        return true;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, RegistrationPaymentContextDto> getPaymentContexts(
        Collection<UUID> registrationIds
    ) {
        if (registrationIds == null || registrationIds.isEmpty()) {
            return Map.of();
        }
        return registrationRepository
            .findAllByIds(registrationIds)
            .stream()
            .collect(
                Collectors.toMap(Registration::id, registration ->
                    new RegistrationPaymentContextDto(
                        registration.id(),
                        registration.workshopId(),
                        registration.studentUserId(),
                        registration.status() != null
                            ? registration.status().name()
                            : null
                    )
                )
            );
    }

    private void registerPostCommitCapacityRelease(UUID workshopId) {
        TransactionSynchronizationManager.registerSynchronization(
            new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        capacityPort.releaseCapacity(workshopId);
                    } catch (Exception e) {
                        log.warn(
                            "Failed to restore Redis capacity for workshop [{}]: {}",
                            workshopId,
                            e.getMessage()
                        );
                    }
                }
            }
        );
    }
}
