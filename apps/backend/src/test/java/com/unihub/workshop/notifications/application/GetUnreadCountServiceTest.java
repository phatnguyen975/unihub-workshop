package com.unihub.workshop.notifications.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.unihub.workshop.notifications.application.ports.out.NotificationRepository;
import com.unihub.workshop.notifications.application.usecases.GetUnreadCountService;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GetUnreadCountServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @InjectMocks
    private GetUnreadCountService service;

    @Test
    @DisplayName(
        "should delegate to repository and return unread count for user"
    )
    void shouldReturnUnreadCount() {
        UUID userId = UUID.randomUUID();
        when(notificationRepository.countUnreadByUserId(userId)).thenReturn(5L);

        long count = service.getUnreadCount(userId);

        assertThat(count).isEqualTo(5L);
        verify(notificationRepository).countUnreadByUserId(userId);
    }

    @Test
    @DisplayName("should throw IllegalArgumentException when userId is null")
    void shouldThrowWhenUserIdIsNull() {
        assertThatThrownBy(() -> service.getUnreadCount(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("userId must not be null");
    }
}
