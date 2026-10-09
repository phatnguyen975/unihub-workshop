package com.unihub.workshop.notifications.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.unihub.workshop.notifications.infrastructure.persistence.NotificationRepositoryAdapter;
import com.unihub.workshop.notifications.infrastructure.persistence.SpringDataNotificationRepository;
import com.unihub.workshop.notifications.infrastructure.persistence.mapper.NotificationPersistenceMapper;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationRepositoryAdapterTest {

    @Mock
    private SpringDataNotificationRepository springDataNotificationRepository;

    @Mock
    private EntityManager entityManager;

    @Mock
    private NotificationPersistenceMapper notificationPersistenceMapper;

    @InjectMocks
    private NotificationRepositoryAdapter adapter;

    @Test
    @DisplayName(
        "should count unread notifications by userId using spring data repository"
    )
    void shouldCountUnreadNotificationsByUserId() {
        UUID userId = UUID.randomUUID();
        when(
            springDataNotificationRepository.countByUserIdAndIsReadFalse(userId)
        ).thenReturn(3L);

        long count = adapter.countUnreadByUserId(userId);

        assertThat(count).isEqualTo(3L);
        verify(springDataNotificationRepository).countByUserIdAndIsReadFalse(
            userId
        );
    }
}
