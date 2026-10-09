package com.unihub.workshop.notifications.infrastructure.persistence.mapper;

import com.unihub.workshop.notifications.domain.model.InAppNotification;
import com.unihub.workshop.notifications.infrastructure.persistence.InAppNotificationJpaEntity;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface NotificationPersistenceMapper {
    @Mapping(target = "isRead", source = "read")
    InAppNotification toDomain(InAppNotificationJpaEntity entity);

    @Mapping(target = "isRead", source = "read")
    InAppNotificationJpaEntity toEntity(InAppNotification domain);

    List<InAppNotification> toDomainList(
        List<InAppNotificationJpaEntity> entities
    );
}
