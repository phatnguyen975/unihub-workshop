package com.unihub.workshop.workers.notifications.inbound.handler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.unihub.workshop.workers.notifications.core.models.MultiChannelDeliveryCommand;
import com.unihub.workshop.workers.notifications.core.ports.in.MultiChannelNotificationDeliveryUseCase;
import com.unihub.workshop.workers.notifications.inbound.dto.RegistrationCanceledEventDto;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RegistrationCanceledNotificationHandlerTest {

    @Mock
    private MultiChannelNotificationDeliveryUseCase deliveryUseCase;

    @Captor
    private ArgumentCaptor<MultiChannelDeliveryCommand> commandCaptor;

    private RegistrationCanceledNotificationHandler handler;

    @BeforeEach
    void setUp() {
        handler = new RegistrationCanceledNotificationHandler(deliveryUseCase);
    }

    @Test
    @DisplayName("Should return exact supported event type and event DTO class")
    void shouldReturnSupportedEventType() {
        assertEquals("RegistrationCanceledEvent", handler.supportedEventType());
        assertEquals(
            RegistrationCanceledEventDto.class,
            handler.eventDtoClass()
        );
    }

    @Test
    @DisplayName(
        "Should handle student cancellation of free registration with in-app, email, and real-time SSE"
    )
    void shouldHandleStudentCanceledFreeRegistration() {
        UUID registrationId = UUID.randomUUID();
        UUID workshopId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        Instant startsAt = Instant.parse("2026-11-20T10:00:00Z");
        Instant endsAt = Instant.parse("2026-11-20T12:00:00Z");

        RegistrationCanceledEventDto dto =
            RegistrationCanceledEventDto.builder()
                .registrationId(registrationId)
                .workshopId(workshopId)
                .studentId(studentId)
                .studentEmail("student@unihub.edu.vn")
                .studentName("Nguyen Van A")
                .workshopTitle("AI & Machine Learning")
                .workshopSlug("ai-and-ml")
                .startsAt(startsAt)
                .endsAt(endsAt)
                .location("Auditorium B")
                .reason("Personal schedule conflict")
                .canceledByRole("STUDENT")
                .refundAmount(null)
                .currency(null)
                .occurredAt(Instant.now())
                .build();

        handler.handle(dto);

        verify(deliveryUseCase).deliverMultiChannel(commandCaptor.capture());
        MultiChannelDeliveryCommand command = commandCaptor.getValue();

        assertEquals(studentId, command.userId());
        assertEquals("student@unihub.edu.vn", command.recipientEmail());
        assertEquals(
            "REGISTRATION_CANCELED:" + registrationId,
            command.idempotencyKey()
        );

        // In-App
        assertTrue(command.sendInApp());
        assertEquals("REGISTRATION_CANCELED", command.inAppEventKey());
        assertEquals("/workshops/ai-and-ml", command.inAppActionUrl());
        assertEquals(
            registrationId.toString(),
            command.inAppPayload().get("registrationId")
        );
        assertEquals(
            workshopId.toString(),
            command.inAppPayload().get("workshopId")
        );
        assertEquals(
            "AI & Machine Learning",
            command.inAppPayload().get("workshopTitle")
        );
        assertEquals("STUDENT", command.inAppPayload().get("canceledByRole"));
        assertEquals(
            "Personal schedule conflict",
            command.inAppPayload().get("reason")
        );

        // Realtime SSE
        assertTrue(command.sendRealtime());
        assertEquals("notification.new", command.realtimeEventType());

        // Email
        assertTrue(command.sendEmail());
        assertEquals(
            "UniHub Workshop — Registration Canceled: AI & Machine Learning",
            command.emailSubject()
        );
        assertEquals("registration-canceled", command.emailTemplateName());
        assertEquals(
            "Nguyen Van A",
            command.emailTemplateModel().get("studentName")
        );
        assertEquals(
            "AI & Machine Learning",
            command.emailTemplateModel().get("workshopTitle")
        );
        assertEquals(
            "STUDENT",
            command.emailTemplateModel().get("canceledByRole")
        );
        assertEquals(
            "Personal schedule conflict",
            command.emailTemplateModel().get("reason")
        );
        assertNull(command.emailTemplateModel().get("refundAmount"));
    }

    @Test
    @DisplayName(
        "Should handle organizer cancellation of paid registration with refund details"
    )
    void shouldHandleOrganizerCanceledPaidRegistrationWithRefund() {
        UUID registrationId = UUID.randomUUID();
        UUID workshopId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        BigDecimal refundAmount = new BigDecimal("75.00");

        RegistrationCanceledEventDto dto =
            RegistrationCanceledEventDto.builder()
                .registrationId(registrationId)
                .workshopId(workshopId)
                .studentId(studentId)
                .studentEmail("student2@unihub.edu.vn")
                .studentName("Le Thi B")
                .workshopTitle("Kubernetes Deep Dive")
                .workshopSlug("k8s-deep-dive")
                .startsAt(Instant.now().plusSeconds(86400))
                .endsAt(Instant.now().plusSeconds(90000))
                .location("Online")
                .reason("Speaker medical emergency")
                .canceledByRole("ORGANIZER")
                .refundAmount(refundAmount)
                .currency("USD")
                .occurredAt(Instant.now())
                .build();

        handler.handle(dto);

        verify(deliveryUseCase).deliverMultiChannel(commandCaptor.capture());
        MultiChannelDeliveryCommand command = commandCaptor.getValue();

        assertEquals(studentId, command.userId());
        assertTrue(command.sendEmail());
        assertEquals(refundAmount, command.inAppPayload().get("refundAmount"));
        assertEquals("USD", command.inAppPayload().get("currency"));
        assertEquals(
            refundAmount,
            command.emailTemplateModel().get("refundAmount")
        );
        assertEquals("USD", command.emailTemplateModel().get("currency"));
        assertEquals(
            "ORGANIZER",
            command.emailTemplateModel().get("canceledByRole")
        );
    }

    @Test
    @DisplayName(
        "Should skip sending email when student email is null or blank"
    )
    void shouldSkipEmailWhenEmailIsBlank() {
        UUID registrationId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();

        RegistrationCanceledEventDto dto =
            RegistrationCanceledEventDto.builder()
                .registrationId(registrationId)
                .studentId(studentId)
                .studentEmail("   ")
                .workshopTitle("Test Workshop")
                .canceledByRole("STUDENT")
                .build();

        handler.handle(dto);

        verify(deliveryUseCase).deliverMultiChannel(commandCaptor.capture());
        MultiChannelDeliveryCommand command = commandCaptor.getValue();

        assertFalse(command.sendEmail());
        assertTrue(command.sendInApp());
        assertTrue(command.sendRealtime());
    }

    @Test
    @DisplayName("Should skip malformed event when event is null")
    void shouldSkipWhenEventIsNull() {
        handler.handle(null);
        verify(deliveryUseCase, never()).deliverMultiChannel(any());
    }

    @Test
    @DisplayName("Should skip malformed event when registrationId is null")
    void shouldSkipWhenRegistrationIdIsNull() {
        RegistrationCanceledEventDto dto =
            RegistrationCanceledEventDto.builder()
                .registrationId(null)
                .studentId(UUID.randomUUID())
                .build();

        handler.handle(dto);
        verify(deliveryUseCase, never()).deliverMultiChannel(any());
    }

    @Test
    @DisplayName("Should skip malformed event when studentId is null")
    void shouldSkipWhenStudentIdIsNull() {
        RegistrationCanceledEventDto dto =
            RegistrationCanceledEventDto.builder()
                .registrationId(UUID.randomUUID())
                .studentId(null)
                .build();

        handler.handle(dto);
        verify(deliveryUseCase, never()).deliverMultiChannel(any());
    }

    @Test
    @DisplayName(
        "Should fallback actionUrl to workshopId when workshopSlug is null"
    )
    void shouldFallbackActionUrlWhenSlugIsNull() {
        UUID registrationId = UUID.randomUUID();
        UUID workshopId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();

        RegistrationCanceledEventDto dto =
            RegistrationCanceledEventDto.builder()
                .registrationId(registrationId)
                .workshopId(workshopId)
                .studentId(studentId)
                .workshopSlug(null)
                .build();

        handler.handle(dto);

        verify(deliveryUseCase).deliverMultiChannel(commandCaptor.capture());
        MultiChannelDeliveryCommand command = commandCaptor.getValue();

        assertEquals("/workshops/" + workshopId, command.inAppActionUrl());
    }
}
