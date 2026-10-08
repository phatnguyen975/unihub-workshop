package com.unihub.workshop.workers.notifications.inbound.handler;

import com.unihub.workshop.workers.notifications.core.models.OrganizationNotificationCommand;
import com.unihub.workshop.workers.notifications.core.ports.in.NotificationEventHandler;
import com.unihub.workshop.workers.notifications.core.ports.in.OrganizationNotificationFanOutUseCase;
import com.unihub.workshop.workers.notifications.inbound.dto.FileCompletedEventDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class FileCompletedNotificationHandler
    implements NotificationEventHandler<FileCompletedEventDto>
{

    private final OrganizationNotificationFanOutUseCase fanOutUseCase;

    @Override
    public String supportedEventType() {
        return "FileCompletedEvent";
    }

    @Override
    public Class<FileCompletedEventDto> eventDtoClass() {
        return FileCompletedEventDto.class;
    }

    @Override
    public void handle(FileCompletedEventDto event) {
        if (event == null || event.organizationId() == null) {
            log.warn(
                "Skipping invalid FileCompletedEventDto: missing organizationId"
            );
            return;
        }

        OrganizationNotificationCommand command =
            new OrganizationNotificationCommand(
                event.organizationId(),
                "FILE_COMPLETED",
                event.sourceFileKey(),
                "FILE_COMPLETED",
                "/admin/import",
                "Student Directory Ingestion Completed",
                "ingestion-completed"
            );

        fanOutUseCase.fanOutOrganizationNotification(command);
    }
}
