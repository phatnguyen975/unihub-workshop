package com.unihub.workshop.registrations.application.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.unihub.workshop.iam.api.IamApi;
import com.unihub.workshop.iam.api.dto.StudentProfileDto;
import com.unihub.workshop.registrations.api.dto.ConfirmPaidRegistrationCommand;
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
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

class RegistrationFacadeTest {

    private RegistrationRepository registrationRepository;
    private QrSignaturePort qrSignaturePort;
    private ApplicationEventPublisher eventPublisher;
    private IamApi iamApi;
    private WorkshopApi workshopApi;
    private CapacityPort capacityPort;
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
    private static final String PROVIDER_PAYMENT_ID = "pi_test_123456";

    @BeforeEach
    void setUp() {
        registrationRepository = mock(RegistrationRepository.class);
        qrSignaturePort = mock(QrSignaturePort.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        iamApi = mock(IamApi.class);
        workshopApi = mock(WorkshopApi.class);
        capacityPort = mock(CapacityPort.class);

        registrationFacade = new RegistrationFacade(
            registrationRepository,
            qrSignaturePort,
            eventPublisher,
            iamApi,
            workshopApi,
            capacityPort
        );
    }

    @Test
    @DisplayName(
        "FR-20, FR-23: Successfully confirms PENDING registration, sets QR ticket, and publishes outbox event"
    )
    void confirmPaidRegistration_PendingRegistration_SuccessfullyConfirms() {
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

        QrTicket qrTicket = new QrTicket(
            "{\"regId\":\"" + REGISTRATION_ID + "\"}",
            "sig_abc_123",
            now
        );

        StudentProfileDto student = new StudentProfileDto(
            STUDENT_USER_ID,
            UUID.randomUUID(),
            "John Doe",
            "student@unihub.edu.vn"
        );

        WorkshopRegistrationDetailsDto workshop =
            new WorkshopRegistrationDetailsDto(
                WORKSHOP_ID,
                UUID.randomUUID(),
                "Advanced Java 25",
                "advanced-java-25",
                "PUBLISHED",
                now.plusSeconds(3600),
                now.plusSeconds(7200),
                BigDecimal.valueOf(50.0),
                100,
                "Hall A"
            );

        when(registrationRepository.findById(REGISTRATION_ID)).thenReturn(
            Optional.of(pending)
        );
        when(
            qrSignaturePort.generateSignedTicket(any(), any(), any(), any())
        ).thenReturn(qrTicket);
        when(iamApi.getStudentProfile(STUDENT_USER_ID)).thenReturn(
            Optional.of(student)
        );
        when(workshopApi.getWorkshopForRegistration(WORKSHOP_ID)).thenReturn(
            Optional.of(workshop)
        );
        when(registrationRepository.save(any(Registration.class))).thenAnswer(
            invocation -> invocation.getArgument(0)
        );

        ConfirmPaidRegistrationCommand command =
            new ConfirmPaidRegistrationCommand(
                REGISTRATION_ID,
                PROVIDER_PAYMENT_ID
            );
        RegistrationConfirmedDto result =
            registrationFacade.confirmPaidRegistration(command);

        assertThat(result).isNotNull();
        assertThat(result.registrationId()).isEqualTo(REGISTRATION_ID);
        assertThat(result.workshopId()).isEqualTo(WORKSHOP_ID);
        assertThat(result.studentUserId()).isEqualTo(STUDENT_USER_ID);
        assertThat(result.qrPayload()).isEqualTo(qrTicket.qrPayload());
        assertThat(result.qrIssuedAt()).isEqualTo(qrTicket.qrIssuedAt());

        ArgumentCaptor<Registration> regCaptor = ArgumentCaptor.forClass(
            Registration.class
        );
        verify(registrationRepository).save(regCaptor.capture());
        Registration savedReg = regCaptor.getValue();
        assertThat(savedReg.status()).isEqualTo(RegistrationStatus.CONFIRMED);
        assertThat(savedReg.qrTicket()).isEqualTo(qrTicket);
        assertThat(savedReg.expiresAt()).isNull();

        ArgumentCaptor<WorkshopRegisteredEvent> eventCaptor =
            ArgumentCaptor.forClass(WorkshopRegisteredEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        WorkshopRegisteredEvent publishedEvent = eventCaptor.getValue();
        assertThat(publishedEvent.registrationId()).isEqualTo(REGISTRATION_ID);
        assertThat(publishedEvent.workshopId()).isEqualTo(WORKSHOP_ID);
        assertThat(publishedEvent.studentEmail()).isEqualTo(
            "student@unihub.edu.vn"
        );
        assertThat(publishedEvent.workshopTitle()).isEqualTo(
            "Advanced Java 25"
        );
        assertThat(publishedEvent.qrSignature()).isEqualTo("sig_abc_123");
    }

    @Test
    @DisplayName("FR-27: Returns null when registration does not exist")
    void confirmPaidRegistration_NotFound_ReturnsNull() {
        when(registrationRepository.findById(REGISTRATION_ID)).thenReturn(
            Optional.empty()
        );

        ConfirmPaidRegistrationCommand command =
            new ConfirmPaidRegistrationCommand(
                REGISTRATION_ID,
                PROVIDER_PAYMENT_ID
            );
        RegistrationConfirmedDto result =
            registrationFacade.confirmPaidRegistration(command);

        assertThat(result).isNull();
        verify(registrationRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName(
        "FR-27: Returns null when registration is not in PENDING status"
    )
    void confirmPaidRegistration_NotPending_ReturnsNull() {
        Registration confirmed = Registration.builder()
            .id(REGISTRATION_ID)
            .workshopId(WORKSHOP_ID)
            .studentUserId(STUDENT_USER_ID)
            .status(RegistrationStatus.CONFIRMED)
            .build();

        when(registrationRepository.findById(REGISTRATION_ID)).thenReturn(
            Optional.of(confirmed)
        );

        ConfirmPaidRegistrationCommand command =
            new ConfirmPaidRegistrationCommand(
                REGISTRATION_ID,
                PROVIDER_PAYMENT_ID
            );
        RegistrationConfirmedDto result =
            registrationFacade.confirmPaidRegistration(command);

        assertThat(result).isNull();
        verify(registrationRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("Resolves registration payment contexts in one batch read")
    void getPaymentContexts_returnsBatchContextsKeyedByRegistrationId() {
        UUID secondRegistrationId = UUID.fromString(
            "e0000002-0000-4000-a000-000000000002"
        );
        Set<UUID> registrationIds = Set.of(
            REGISTRATION_ID,
            secondRegistrationId
        );

        Registration pending = Registration.builder()
            .id(REGISTRATION_ID)
            .workshopId(WORKSHOP_ID)
            .studentUserId(STUDENT_USER_ID)
            .status(RegistrationStatus.PENDING)
            .build();
        Registration confirmed = Registration.builder()
            .id(secondRegistrationId)
            .workshopId(WORKSHOP_ID)
            .studentUserId(STUDENT_USER_ID)
            .status(RegistrationStatus.CONFIRMED)
            .build();

        when(registrationRepository.findAllByIds(registrationIds)).thenReturn(
            List.of(pending, confirmed)
        );

        Map<UUID, RegistrationPaymentContextDto> contexts =
            registrationFacade.getPaymentContexts(registrationIds);

        assertThat(contexts).containsExactlyInAnyOrderEntriesOf(
            Map.of(
                REGISTRATION_ID,
                new RegistrationPaymentContextDto(
                    REGISTRATION_ID,
                    WORKSHOP_ID,
                    "PENDING"
                ),
                secondRegistrationId,
                new RegistrationPaymentContextDto(
                    secondRegistrationId,
                    WORKSHOP_ID,
                    "CONFIRMED"
                )
            )
        );
        verify(registrationRepository).findAllByIds(registrationIds);
    }

    @Test
    @DisplayName(
        "Returns an empty payment context map without querying when no ids are supplied"
    )
    void getPaymentContexts_withNoIds_returnsEmptyMapWithoutQuerying() {
        assertThat(registrationFacade.getPaymentContexts(Set.of())).isEmpty();
        assertThat(registrationFacade.getPaymentContexts(null)).isEmpty();
        verify(registrationRepository, never()).findAllByIds(any());
    }
}
