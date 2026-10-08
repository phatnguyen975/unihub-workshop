package com.unihub.workshop.workers.notifications.inbound.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.unihub.workshop.workers.notifications.core.models.OrganizationNotificationCommand;
import com.unihub.workshop.workers.notifications.core.ports.in.OrganizationNotificationFanOutUseCase;
import com.unihub.workshop.workers.notifications.inbound.dto.FileCompletedEventDto;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FileCompletedNotificationHandlerTest {

    @Mock
    private OrganizationNotificationFanOutUseCase fanOutUseCase;

    @InjectMocks
    private FileCompletedNotificationHandler handler;

    @Test
    @DisplayName("Should return FileCompletedEvent as supported event type")
    void shouldReturnSupportedEventType() {
        assertThat(handler.supportedEventType()).isEqualTo(
            "FileCompletedEvent"
        );
        assertThat(handler.eventDtoClass()).isEqualTo(
            FileCompletedEventDto.class
        );
    }

    @Test
    @DisplayName("Should map event to command and delegate to fanOutUseCase")
    void shouldHandleValidEvent() {
        UUID orgId = UUID.randomUUID();
        FileCompletedEventDto event = new FileCompletedEventDto(
            orgId,
            "uploads/students-batch-2026.csv"
        );

        handler.handle(event);

        ArgumentCaptor<OrganizationNotificationCommand> captor =
            ArgumentCaptor.forClass(OrganizationNotificationCommand.class);
        verify(fanOutUseCase).fanOutOrganizationNotification(captor.capture());

        OrganizationNotificationCommand cmd = captor.getValue();
        assertThat(cmd.organizationId()).isEqualTo(orgId);
        assertThat(cmd.idempotencyPrefix()).isEqualTo("FILE_COMPLETED");
        assertThat(cmd.sourceFileKey()).isEqualTo(
            "uploads/students-batch-2026.csv"
        );
        assertThat(cmd.inAppEventKey()).isEqualTo("FILE_COMPLETED");
        assertThat(cmd.inAppActionUrl()).isEqualTo("/admin/import");
        assertThat(cmd.emailSubject()).isEqualTo(
            "Student Directory Ingestion Completed"
        );
        assertThat(cmd.emailTemplateName()).isEqualTo("ingestion-completed");
    }

    @Test
    @DisplayName("Should skip when event is null or organizationId is null")
    void shouldSkipWhenInvalid() {
        handler.handle(null);
        handler.handle(new FileCompletedEventDto(null, "some.csv"));

        verify(fanOutUseCase, never()).fanOutOrganizationNotification(any());
    }
}
