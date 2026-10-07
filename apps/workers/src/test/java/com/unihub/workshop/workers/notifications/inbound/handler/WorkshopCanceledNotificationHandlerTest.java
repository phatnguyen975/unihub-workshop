package com.unihub.workshop.workers.notifications.inbound.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.unihub.workshop.workers.notifications.core.models.WorkshopNotificationCommand;
import com.unihub.workshop.workers.notifications.core.ports.in.WorkshopNotificationFanOutUseCase;
import com.unihub.workshop.workers.notifications.inbound.dto.WorkshopCanceledEventDto;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WorkshopCanceledNotificationHandlerTest {

    @Mock
    private WorkshopNotificationFanOutUseCase fanOutUseCase;

    @InjectMocks
    private WorkshopCanceledNotificationHandler handler;

    @Test
    @DisplayName("Should return WorkshopCanceledEvent as supported event type")
    void shouldReturnSupportedEventType() {
        assertThat(handler.supportedEventType()).isEqualTo(
            "WorkshopCanceledEvent"
        );
        assertThat(handler.eventDtoClass()).isEqualTo(
            WorkshopCanceledEventDto.class
        );
    }

    @Test
    @DisplayName("Should map event to command and delegate to fanOutUseCase")
    void shouldHandleValidEvent() {
        UUID workshopId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        Instant startsAt = Instant.parse("2026-10-15T09:00:00Z");
        Instant endsAt = Instant.parse("2026-10-15T11:00:00Z");
        Instant canceledAt = Instant.now();

        WorkshopCanceledEventDto event = new WorkshopCanceledEventDto(
            workshopId,
            orgId,
            "AI in Healthcare",
            "ai-in-healthcare",
            startsAt,
            endsAt,
            canceledAt,
            "Speaker unavailable",
            Instant.now()
        );

        handler.handle(event);

        ArgumentCaptor<WorkshopNotificationCommand> captor =
            ArgumentCaptor.forClass(WorkshopNotificationCommand.class);
        verify(fanOutUseCase).fanOutWorkshopNotification(captor.capture());

        WorkshopNotificationCommand cmd = captor.getValue();
        assertThat(cmd.workshopId()).isEqualTo(workshopId);
        assertThat(cmd.idempotencyPrefix()).isEqualTo(
            "WORKSHOP_CANCELED:" + workshopId
        );
        assertThat(cmd.slug()).isEqualTo("ai-in-healthcare");
        assertThat(cmd.inAppEventKey()).isEqualTo("WORKSHOP_CANCELED");
        assertThat(cmd.inAppActionUrl()).isEqualTo(
            "/workshops/ai-in-healthcare"
        );
        assertThat(cmd.emailSubject()).isEqualTo(
            "Workshop Canceled: AI in Healthcare"
        );
        assertThat(cmd.emailTemplateName()).isEqualTo(
            "email/workshop-canceled"
        );
    }

    @Test
    @DisplayName("Should skip when event is null or workshopId is null")
    void shouldSkipWhenInvalid() {
        handler.handle(null);
        handler.handle(
            new WorkshopCanceledEventDto(
                null,
                null,
                "Title",
                "slug",
                null,
                null,
                null,
                "Reason",
                null
            )
        );

        verify(fanOutUseCase, never()).fanOutWorkshopNotification(any());
    }
}
