package com.unihub.workshop.workers.notifications.inbound.handler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.unihub.workshop.workers.notifications.core.ports.in.MultiChannelNotificationDeliveryUseCase;
import com.unihub.workshop.workers.notifications.core.ports.out.QrCodeGeneratorPort;
import com.unihub.workshop.workers.notifications.inbound.dto.WorkshopRegisteredEventDto;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WorkshopRegisteredNotificationHandlerTest {

    @Mock
    private MultiChannelNotificationDeliveryUseCase deliveryUseCase;

    @Mock
    private QrCodeGeneratorPort qrCodeGeneratorPort;

    private WorkshopRegisteredNotificationHandler handler;

    @BeforeEach
    void setUp() {
        handler = new WorkshopRegisteredNotificationHandler(
            deliveryUseCase,
            qrCodeGeneratorPort
        );
    }

    @Test
    @DisplayName("Should return exact supported event type")
    void shouldReturnSupportedEventType() {
        assertEquals("WorkshopRegisteredEvent", handler.supportedEventType());
        assertEquals(WorkshopRegisteredEventDto.class, handler.eventDtoClass());
    }

    @Test
    @DisplayName(
        "Should handle WorkshopRegisteredEvent: generates QR code and dispatches multi-channel notification"
    )
    void shouldHandleWorkshopRegisteredEvent() {
        UUID registrationId = UUID.randomUUID();
        UUID workshopId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        Instant now = Instant.now();

        String qrPayloadJson =
            "{\"registrationId\":\"" +
            registrationId +
            "\",\"workshopId\":\"" +
            workshopId +
            "\"}";
        String qrSignature = "base64-signature-123";
        byte[] dummyQrBytes = new byte[] {
            (byte) 0x89,
            0x50,
            0x4E,
            0x47,
            1,
            2,
            3,
        };

        when(
            qrCodeGeneratorPort.generateQrPng(
                eq(
                    "{\"payload\":" +
                        qrPayloadJson +
                        ",\"signature\":\"" +
                        qrSignature +
                        "\"}"
                ),
                eq(300),
                eq(300)
            )
        ).thenReturn(dummyQrBytes);

        WorkshopRegisteredEventDto dto = WorkshopRegisteredEventDto.builder()
            .registrationId(registrationId)
            .workshopId(workshopId)
            .studentId(studentId)
            .studentEmail("student@unihub.edu.vn")
            .studentName("Nguyen Van An")
            .workshopTitle("Spring Boot Microservices")
            .workshopSlug("spring-boot-microservices")
            .startsAt(now.plusSeconds(3600))
            .endsAt(now.plusSeconds(7200))
            .location("Auditorium C")
            .qrPayload(qrPayloadJson)
            .qrSignature(qrSignature)
            .qrIssuedAt(now)
            .occurredAt(now)
            .build();

        handler.handle(dto);

        verify(qrCodeGeneratorPort).generateQrPng(
            eq(
                "{\"payload\":" +
                    qrPayloadJson +
                    ",\"signature\":\"" +
                    qrSignature +
                    "\"}"
            ),
            eq(300),
            eq(300)
        );

        verify(deliveryUseCase).deliverMultiChannel(
            argThat(
                cmd ->
                    cmd.userId().equals(studentId) &&
                    "student@unihub.edu.vn".equals(cmd.recipientEmail()) &&
                    ("WORKSHOP_REGISTERED:" + registrationId).equals(
                        cmd.idempotencyKey()
                    ) &&
                    cmd.sendInApp() &&
                    "WORKSHOP_REGISTERED".equals(cmd.inAppEventKey()) &&
                    "/workshops/spring-boot-microservices".equals(
                        cmd.inAppActionUrl()
                    ) &&
                    cmd
                        .inAppPayload()
                        .get("workshopTitle")
                        .equals("Spring Boot Microservices") &&
                    cmd.sendRealtime() &&
                    "notification.new".equals(cmd.realtimeEventType()) &&
                    cmd.sendEmail() &&
                    "workshop-registration-confirmation".equals(
                        cmd.emailTemplateName()
                    ) &&
                    "Spring Boot Microservices".equals(
                        cmd.emailTemplateModel().get("workshopTitle")
                    ) &&
                    cmd.inlineAttachments().containsKey("ticket-qr") &&
                    cmd.inlineAttachments().get("ticket-qr") == dummyQrBytes
            )
        );
    }

    @Test
    @DisplayName(
        "Should handle WorkshopRegisteredEvent without QR payload without generating QR code"
    )
    void shouldHandleWorkshopRegisteredEvent_withoutQr() {
        UUID registrationId = UUID.randomUUID();
        UUID workshopId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        Instant now = Instant.now();

        WorkshopRegisteredEventDto dto = WorkshopRegisteredEventDto.builder()
            .registrationId(registrationId)
            .workshopId(workshopId)
            .studentId(studentId)
            .studentEmail("student@unihub.edu.vn")
            .studentName("Nguyen Van An")
            .workshopTitle("Spring Boot Microservices")
            .workshopSlug("spring-boot-microservices")
            .startsAt(now.plusSeconds(3600))
            .endsAt(now.plusSeconds(7200))
            .location("Auditorium C")
            .occurredAt(now)
            .build();

        handler.handle(dto);

        verify(qrCodeGeneratorPort, never()).generateQrPng(
            any(),
            eq(300),
            eq(300)
        );

        verify(deliveryUseCase).deliverMultiChannel(
            argThat(
                cmd ->
                    cmd.userId().equals(studentId) &&
                    cmd.inlineAttachments().isEmpty()
            )
        );
    }

    @Test
    @DisplayName("Should skip when event or critical identifiers are missing")
    void shouldSkipWhenInvalid() {
        handler.handle(null);
        handler.handle(
            WorkshopRegisteredEventDto.builder()
                .workshopId(UUID.randomUUID())
                .studentId(UUID.randomUUID())
                .studentEmail("test@test.com")
                .studentName("Name")
                .workshopTitle("Title")
                .workshopSlug("slug")
                .build()
        );
        handler.handle(
            WorkshopRegisteredEventDto.builder()
                .registrationId(UUID.randomUUID())
                .workshopId(UUID.randomUUID())
                .studentEmail("test@test.com")
                .studentName("Name")
                .workshopTitle("Title")
                .workshopSlug("slug")
                .build()
        );

        verify(deliveryUseCase, never()).deliverMultiChannel(any());
        verify(qrCodeGeneratorPort, never()).generateQrPng(
            any(),
            eq(300),
            eq(300)
        );
    }
}
