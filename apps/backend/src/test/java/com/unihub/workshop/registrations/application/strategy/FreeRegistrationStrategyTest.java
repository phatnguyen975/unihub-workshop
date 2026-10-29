package com.unihub.workshop.registrations.application.strategy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.unihub.workshop.iam.api.dto.StudentProfileDto;
import com.unihub.workshop.registrations.application.ports.out.QrSignaturePort;
import com.unihub.workshop.registrations.application.ports.out.RegistrationRepository;
import com.unihub.workshop.registrations.application.ports.out.WorkshopRegistrationBroadcaster;
import com.unihub.workshop.registrations.domain.model.QrTicket;
import com.unihub.workshop.registrations.domain.model.Registration;
import com.unihub.workshop.registrations.domain.model.RegistrationStatus;
import com.unihub.workshop.shared.events.WorkshopRegisteredEvent;
import com.unihub.workshop.workshops.api.dto.WorkshopRegistrationDetailsDto;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class FreeRegistrationStrategyTest {

    private RegistrationRepository registrationRepository;
    private QrSignaturePort qrSignatureService;
    private ApplicationEventPublisher eventPublisher;
    private WorkshopRegistrationBroadcaster workshopRegistrationBroadcaster;
    private FreeRegistrationStrategy strategy;

    private final UUID workshopId = UUID.randomUUID();
    private final UUID studentUserId = UUID.randomUUID();
    private final UUID idempotencyKeyId = UUID.randomUUID();
    private final Instant now = Instant.parse("2026-10-28T14:00:00Z");

    @BeforeEach
    void setUp() {
        registrationRepository = mock(RegistrationRepository.class);
        qrSignatureService = mock(QrSignaturePort.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        workshopRegistrationBroadcaster = mock(
            WorkshopRegistrationBroadcaster.class
        );

        strategy = new FreeRegistrationStrategy(
            registrationRepository,
            qrSignatureService,
            eventPublisher,
            workshopRegistrationBroadcaster
        );
    }

    @Test
    @DisplayName("supports returns true for zero price workshop")
    void supports_ZeroPrice_ReturnsTrue() {
        WorkshopRegistrationDetailsDto freeWorkshop =
            new WorkshopRegistrationDetailsDto(
                workshopId,
                UUID.randomUUID(),
                "Title",
                "slug",
                "PUBLISHED",
                now,
                now.plusSeconds(3600),
                BigDecimal.ZERO,
                100,
                "Room 1"
            );
        assertThat(strategy.supports(freeWorkshop)).isTrue();
    }

    @Test
    @DisplayName(
        "register creates confirmed registration and broadcasts seat counts (FR-15, FR-17)"
    )
    void register_FreeWorkshop_ConfirmsAndBroadcastsSeatCounts() {
        WorkshopRegistrationDetailsDto workshop =
            new WorkshopRegistrationDetailsDto(
                workshopId,
                UUID.randomUUID(),
                "Title",
                "slug",
                "PUBLISHED",
                now,
                now.plusSeconds(3600),
                BigDecimal.ZERO,
                100,
                "Room 1"
            );

        StudentProfileDto student = new StudentProfileDto(
            studentUserId,
            UUID.randomUUID(),
            "Nguyen Van A",
            "student@hcmus.edu.vn",
            "20123456"
        );

        QrTicket qrTicket = new QrTicket("payload", "signature", now);
        when(
            qrSignatureService.generateSignedTicket(any(), any(), any(), any())
        ).thenReturn(qrTicket);
        when(registrationRepository.save(any(Registration.class))).thenAnswer(
            invocation -> invocation.getArgument(0)
        );

        Registration result = strategy.register(
            student,
            workshop,
            idempotencyKeyId
        );

        assertThat(result.status()).isEqualTo(RegistrationStatus.CONFIRMED);
        assertThat(result.workshopId()).isEqualTo(workshopId);
        assertThat(result.studentUserId()).isEqualTo(studentUserId);

        verify(eventPublisher).publishEvent(any(WorkshopRegisteredEvent.class));
        verify(workshopRegistrationBroadcaster).broadcast(workshopId);
    }
}
