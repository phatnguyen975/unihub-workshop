package com.unihub.workshop.notifications.domain.repository;

import com.unihub.workshop.notifications.domain.model.InAppNotification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface InAppNotificationRepository extends JpaRepository<InAppNotification, UUID> {
}
