package com.unihub.workshop.notifications.domain.repository;

import com.unihub.workshop.notifications.domain.model.NotificationLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface NotificationLogRepository extends JpaRepository<NotificationLog, UUID> {
}
