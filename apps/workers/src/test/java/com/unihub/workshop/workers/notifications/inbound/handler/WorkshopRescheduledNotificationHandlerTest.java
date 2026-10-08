package com.unihub.workshop.workers.notifications.inbound.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.unihub.workshop.workers.notifications.core.models.WorkshopNotificationCommand;
import com.unihub.workshop.workers.notifications.core.ports.in.WorkshopNotificationFanOutUseCase;
import com.unihub.workshop.workers.notifications.inbound.dto.WorkshopRescheduledEventDto;
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
class WorkshopRescheduledNotificationHandlerTest {

    @Mock
    private WorkshopNotificationFanOutUseCase fanOutUseCase;

    @InjectMocks
    private WorkshopRescheduledNotificationHandler handler;

    @Test
    @DisplayName(
        "Should return WorkshopRescheduledEvent as supported event type"
    )
    void shouldReturnSupportedEventType() {
        assertThat(handler.supportedEventType()).isEqualTo(
            "WorkshopRescheduledEvent"
        );
        assertThat(handler.eventDtoClass()).isEqualTo(
            WorkshopRescheduledEventDto.class
        );
    }

    @Test
    @DisplayName("Should map event to command and delegate to fanOutUseCase")
    void shouldHandleValidEvent() {
        UUID workshopId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        Instant prevStart = Instant.parse("2026-10-15T09:00:00Z");
        Instant prevEnd = Instant.parse("2026-10-15T11:00:00Z");
        Instant newStart = Instant.parse("2026-10-18T10:00:00Z");
        Instant newEnd = Instant.parse("2026-10-18T12:00:00Z");

        WorkshopRescheduledEventDto event = new WorkshopRescheduledEventDto(
            workshopId,
            orgId,
            "Cloud Native Microservices",
            "cloud-native-microservices",
            "Hall B",
            "Asia/Ho_Chi_Minh",
            prevStart,
            prevEnd,
            newStart,
            newEnd,
            Instant.now(),
            Instant.now()
        );

        handler.handle(event);

        ArgumentCaptor<WorkshopNotificationCommand> captor =
            ArgumentCaptor.forClass(WorkshopNotificationCommand.class);
        verify(fanOutUseCase).fanOutWorkshopNotification(captor.capture());

        WorkshopNotificationCommand cmd = captor.getValue();
        assertThat(cmd.workshopId()).isEqualTo(workshopId);
        assertThat(cmd.idempotencyPrefix()).contains(
            "WORKSHOP_RESCHEDULED:" + workshopId
        );
        assertThat(cmd.slug()).isEqualTo("cloud-native-microservices");
        assertThat(cmd.inAppEventKey()).isEqualTo("WORKSHOP_RESCHEDULED");
        assertThat(cmd.inAppActionUrl()).isEqualTo(
            "/workshops/cloud-native-microservices"
        );
        assertThat(cmd.inAppPayload())
            .containsEntry("previousStartsAt", prevStart.toString())
            .containsEntry("previousEndsAt", prevEnd.toString())
            .containsEntry("newStartsAt", newStart.toString())
            .containsEntry("newEndsAt", newEnd.toString());
        assertThat(cmd.emailSubject()).isEqualTo(
            "Workshop Rescheduled: Cloud Native Microservices"
        );
        assertThat(cmd.emailTemplateName()).isEqualTo("workshop-rescheduled");
        assertThat(cmd.emailTemplateModel())
            .containsEntry("previousStartsAt", "2026-10-15 16:00 (GMT+7)")
            .containsEntry("previousEndsAt", "2026-10-15 18:00 (GMT+7)")
            .containsEntry("newStartsAt", "2026-10-18 17:00 (GMT+7)")
            .containsEntry("newEndsAt", "2026-10-18 19:00 (GMT+7)");
    }

    @Test
    @DisplayName("Should skip when event is null or workshopId is null")
    void shouldSkipWhenInvalid() {
        handler.handle(null);
        handler.handle(
            new WorkshopRescheduledEventDto(
                null,
                null,
                "Title",
                "slug",
                "loc",
                "UTC",
                null,
                null,
                null,
                null,
                null,
                null
            )
        );

        verify(fanOutUseCase, never()).fanOutWorkshopNotification(any());
    }
}
