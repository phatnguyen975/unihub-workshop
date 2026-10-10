package com.unihub.workshop.notifications.presentation.controllers;

import static org.mockito.Mockito.mock;

import com.unihub.workshop.notifications.application.ports.in.GetNotificationsUseCase;
import com.unihub.workshop.notifications.application.ports.in.GetUnreadCountUseCase;
import com.unihub.workshop.notifications.application.ports.in.MarkAllNotificationsReadUseCase;
import com.unihub.workshop.notifications.application.ports.in.MarkNotificationReadUseCase;
import com.unihub.workshop.notifications.presentation.mapper.NotificationPresentationMapper;
import com.unihub.workshop.notifications.presentation.mapper.NotificationPresentationMapperImpl;

public class NotificationControllerTestBuilder {

    private GetUnreadCountUseCase getUnreadCountUseCase = mock(
        GetUnreadCountUseCase.class
    );
    private GetNotificationsUseCase getNotificationsUseCase = mock(
        GetNotificationsUseCase.class
    );
    private MarkNotificationReadUseCase markNotificationReadUseCase = mock(
        MarkNotificationReadUseCase.class
    );
    private MarkAllNotificationsReadUseCase markAllNotificationsReadUseCase =
        mock(MarkAllNotificationsReadUseCase.class);
    private NotificationPresentationMapper notificationPresentationMapper =
        new NotificationPresentationMapperImpl();

    public static NotificationControllerTestBuilder builder() {
        return new NotificationControllerTestBuilder();
    }

    public NotificationControllerTestBuilder withGetUnreadCountUseCase(
        GetUnreadCountUseCase getUnreadCountUseCase
    ) {
        this.getUnreadCountUseCase = getUnreadCountUseCase;
        return this;
    }

    public NotificationControllerTestBuilder withGetNotificationsUseCase(
        GetNotificationsUseCase getNotificationsUseCase
    ) {
        this.getNotificationsUseCase = getNotificationsUseCase;
        return this;
    }

    public NotificationControllerTestBuilder withMarkNotificationReadUseCase(
        MarkNotificationReadUseCase markNotificationReadUseCase
    ) {
        this.markNotificationReadUseCase = markNotificationReadUseCase;
        return this;
    }

    public NotificationControllerTestBuilder withMarkAllNotificationsReadUseCase(
        MarkAllNotificationsReadUseCase markAllNotificationsReadUseCase
    ) {
        this.markAllNotificationsReadUseCase = markAllNotificationsReadUseCase;
        return this;
    }

    public NotificationControllerTestBuilder withNotificationPresentationMapper(
        NotificationPresentationMapper notificationPresentationMapper
    ) {
        this.notificationPresentationMapper = notificationPresentationMapper;
        return this;
    }

    public NotificationController build() {
        return new NotificationController(
            getUnreadCountUseCase,
            getNotificationsUseCase,
            markNotificationReadUseCase,
            markAllNotificationsReadUseCase,
            notificationPresentationMapper
        );
    }
}
