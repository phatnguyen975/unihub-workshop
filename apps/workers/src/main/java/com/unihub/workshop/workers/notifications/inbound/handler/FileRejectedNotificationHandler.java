package com.unihub.workshop.workers.notifications.inbound.handler;

import com.unihub.workshop.workers.notifications.core.models.OrganizationNotificationCommand;
import com.unihub.workshop.workers.notifications.core.ports.in.NotificationEventHandler;
import com.unihub.workshop.workers.notifications.core.ports.in.OrganizationNotificationFanOutUseCase;
import com.unihub.workshop.workers.notifications.inbound.dto.FileRejectedEventDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class FileRejectedNotificationHandler
    implements NotificationEventHandler<FileRejectedEventDto>
{

    private final OrganizationNotificationFanOutUseCase fanOutUseCase;

    @Override
    public String supportedEventType() {
        return "FileRejectedEvent";
    }

    @Override
    public Class<FileRejectedEventDto> eventDtoClass() {
        return FileRejectedEventDto.class;
    }

    @Override
    public void handle(FileRejectedEventDto event) {
        if (event == null || event.organizationId() == null) {
            log.warn(
                "Skipping invalid FileRejectedEventDto: missing organizationId"
            );
            return;
        }

        OrganizationNotificationCommand command =
            new OrganizationNotificationCommand(
                event.organizationId(),
                "FILE_REJECTED",
                event.sourceFileKey(),
                "FILE_REJECTED",
                "/admin/import",
                "Student Directory Ingestion Rejected",
                "email/ingestion-rejected"
            );

        fanOutUseCase.fanOutOrganizationNotification(command);
    }
}
