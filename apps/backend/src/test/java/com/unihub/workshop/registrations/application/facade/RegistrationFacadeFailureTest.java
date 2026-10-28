package com.unihub.workshop.registrations.application.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.unihub.workshop.iam.api.IamApi;
import com.unihub.workshop.registrations.api.dto.FailPaidRegistrationCommand;
import com.unihub.workshop.registrations.application.ports.out.CapacityPort;
import com.unihub.workshop.registrations.application.ports.out.QrSignaturePort;
import com.unihub.workshop.registrations.application.ports.out.RegistrationRepository;
import com.unihub.workshop.registrations.application.ports.out.WorkshopRegistrationBroadcaster;
import com.unihub.workshop.registrations.domain.model.Registration;
import com.unihub.workshop.registrations.domain.model.RegistrationStatus;
import com.unihub.workshop.workshops.api.WorkshopApi;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class RegistrationFacadeFailureTest {

    private RegistrationRepository registrationRepository;
    private QrSignaturePort qrSignaturePort;
    private ApplicationEventPublisher eventPublisher;
    private IamApi iamApi;
    private WorkshopApi workshopApi;
    private CapacityPort capacityPort;
    private WorkshopRegistrationBroadcaster workshopRegistrationBroadcaster;
    private RegistrationFacade registrationFacade;

    private static final UUID REGISTRATION_ID = UUID.fromString(
        "e0000001-0000-4000-a000-000000000001"
    );
    private static final UUID WORKSHOP_ID = UUID.fromString(
        "f0000001-0000-4000-a000-000000000001"
    );
    private static final UUID STUDENT_USER_ID = UUID.fromString(
        "a0000001-0000-4000-a000-000000000001"
    );
    private static final UUID IDEMPOTENCY_KEY_ID = UUID.fromString(
        "b0000001-0000-4000-a000-000000000001"
    );

    @BeforeEach
    void setUp() {
        registrationRepository = mock(RegistrationRepository.class);
        qrSignaturePort = mock(QrSignaturePort.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        iamApi = mock(IamApi.class);
        workshopApi = mock(WorkshopApi.class);
        capacityPort = mock(CapacityPort.class);

        workshopRegistrationBroadcaster = mock(
            WorkshopRegistrationBroadcaster.class
        );

        registrationFacade = new RegistrationFacade(
            registrationRepository,
            qrSignaturePort,
            eventPublisher,
            iamApi,
            workshopApi,
            capacityPort,
            workshopRegistrationBroadcaster
        );
    }

    @Test
    @DisplayName(
        "FR-21, BR-08: Releases Redis capacity only after the failure transaction commits"
    )
    void failPaidRegistration_PendingRegistration_ReleasesCapacityAfterCommit() {
        Instant now = Instant.now();
        Registration pending = Registration.createPendingPaidRegistration(
            REGISTRATION_ID,
            WORKSHOP_ID,
            STUDENT_USER_ID,
            BigDecimal.valueOf(50.0),
            "USD",
            IDEMPOTENCY_KEY_ID,
            "pi_secret_123",
            now.plusSeconds(1800)
        );

        when(registrationRepository.findById(REGISTRATION_ID)).thenReturn(
            Optional.of(pending)
        );
        when(registrationRepository.save(any(Registration.class))).thenAnswer(
            invocation -> invocation.getArgument(0)
        );

        FailPaidRegistrationCommand command = new FailPaidRegistrationCommand(
            REGISTRATION_ID,
            "card_declined"
        );
        TransactionSynchronizationManager.initSynchronization();
        try {
            boolean result = registrationFacade.failPaidRegistration(command);

            assertThat(result).isTrue();

            ArgumentCaptor<Registration> regCaptor = ArgumentCaptor.forClass(
                Registration.class
            );
            verify(registrationRepository).save(regCaptor.capture());
            Registration savedReg = regCaptor.getValue();
            assertThat(savedReg.status()).isEqualTo(RegistrationStatus.FAILED);

            verify(capacityPort, never()).releaseCapacity(WORKSHOP_ID);
            TransactionSynchronizationManager.getSynchronizations().forEach(
                TransactionSynchronization::afterCommit
            );
            verify(capacityPort).releaseCapacity(WORKSHOP_ID);
            verify(eventPublisher, never()).publishEvent(any());
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("FR-27: Returns false when registration is not found")
    void failPaidRegistration_NotFound_ReturnsFalse() {
        when(registrationRepository.findById(REGISTRATION_ID)).thenReturn(
            Optional.empty()
        );

        FailPaidRegistrationCommand command = new FailPaidRegistrationCommand(
            REGISTRATION_ID,
            "card_declined"
        );
        boolean result = registrationFacade.failPaidRegistration(command);

        assertThat(result).isFalse();
        verify(registrationRepository, never()).save(any());
        verify(capacityPort, never()).releaseCapacity(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName(
        "FR-27: Returns false when registration is not in PENDING status"
    )
    void failPaidRegistration_NotPending_ReturnsFalse() {
        Registration confirmed = Registration.builder()
            .id(REGISTRATION_ID)
            .workshopId(WORKSHOP_ID)
            .studentUserId(STUDENT_USER_ID)
            .status(RegistrationStatus.CONFIRMED)
            .build();

        when(registrationRepository.findById(REGISTRATION_ID)).thenReturn(
            Optional.of(confirmed)
        );

        FailPaidRegistrationCommand command = new FailPaidRegistrationCommand(
            REGISTRATION_ID,
            "card_declined"
        );
        boolean result = registrationFacade.failPaidRegistration(command);

        assertThat(result).isFalse();
        verify(registrationRepository, never()).save(any());
        verify(capacityPort, never()).releaseCapacity(any());
        verify(eventPublisher, never()).publishEvent(any());
    }
}
