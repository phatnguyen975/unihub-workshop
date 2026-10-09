package com.unihub.workshop.notifications.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.unihub.workshop.notifications.domain.model.InAppNotification;
import com.unihub.workshop.notifications.infrastructure.persistence.InAppNotificationJpaEntity;
import com.unihub.workshop.notifications.infrastructure.persistence.NotificationRepositoryAdapter;
import com.unihub.workshop.notifications.infrastructure.persistence.SpringDataNotificationRepository;
import com.unihub.workshop.notifications.infrastructure.persistence.mapper.NotificationPersistenceMapper;
import jakarta.persistence.EntityManager;
import java.util.Optional;
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

    @Test
    @DisplayName("findById should find entity and map to domain")
    void findById_whenFound_returnsMappedDomain() {
        UUID id = UUID.randomUUID();
        InAppNotificationJpaEntity entity = InAppNotificationJpaEntity.builder()
            .id(id)
            .build();
        InAppNotification domain = InAppNotification.builder()
            .id(id)
            .userId(UUID.randomUUID())
            .eventKey("EVENT")
            .build();

        when(springDataNotificationRepository.findById(id)).thenReturn(
            Optional.of(entity)
        );
        when(notificationPersistenceMapper.toDomain(entity)).thenReturn(domain);

        Optional<InAppNotification> result = adapter.findById(id);

        assertThat(result).contains(domain);
        verify(springDataNotificationRepository).findById(id);
        verify(notificationPersistenceMapper).toDomain(entity);
    }

    @Test
    @DisplayName("findById should return empty optional when not found")
    void findById_whenNotFound_returnsEmpty() {
        UUID id = UUID.randomUUID();
        when(springDataNotificationRepository.findById(id)).thenReturn(
            Optional.empty()
        );

        Optional<InAppNotification> result = adapter.findById(id);

        assertThat(result).isEmpty();
        verify(springDataNotificationRepository).findById(id);
    }

    @Test
    @DisplayName(
        "save should map to entity, persist with spring data repository, and map back to domain"
    )
    void save_persistsEntityAndReturnsMappedDomain() {
        UUID id = UUID.randomUUID();
        InAppNotification domain = InAppNotification.builder()
            .id(id)
            .userId(UUID.randomUUID())
            .eventKey("EVENT")
            .build();
        InAppNotificationJpaEntity entity = InAppNotificationJpaEntity.builder()
            .id(id)
            .build();
        InAppNotificationJpaEntity savedEntity =
            InAppNotificationJpaEntity.builder().id(id).build();

        when(notificationPersistenceMapper.toEntity(domain)).thenReturn(entity);
        when(springDataNotificationRepository.save(entity)).thenReturn(
            savedEntity
        );
        when(notificationPersistenceMapper.toDomain(savedEntity)).thenReturn(
            domain
        );

        InAppNotification result = adapter.save(domain);

        assertThat(result).isEqualTo(domain);
        verify(notificationPersistenceMapper).toEntity(domain);
        verify(springDataNotificationRepository).save(entity);
        verify(notificationPersistenceMapper).toDomain(savedEntity);
    }
}
