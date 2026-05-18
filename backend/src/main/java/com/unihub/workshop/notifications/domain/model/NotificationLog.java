package com.unihub.workshop.notifications.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "notification_logs")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor
public class NotificationLog {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "registration_id")
    @Setter
    private UUID registrationId;

    @Column(name = "event_type", nullable = false)
    @Setter
    private String eventType;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false)
    @Setter
    private NotificationChannel channel = NotificationChannel.EMAIL;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Setter
    private NotificationStatus status = NotificationStatus.QUEUED;

    @Column(name = "recipient_address", nullable = false)
    @Setter
    private String recipientAddress;

    @Column(name = "provider_message_id")
    @Setter
    private String providerMessageId;

    @Column(name = "error_message")
    @Setter
    private String errorMessage;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", columnDefinition = "jsonb")
    @Setter
    private String payload;

    @Column(name = "status_at", nullable = false)
    @Setter
    private OffsetDateTime statusAt;

    @Column(name = "retry_count", nullable = false)
    @Setter
    private int retryCount = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    private void onCreate() {
        if (this.id == null) {
            this.id = UUID.randomUUID();
        }
        OffsetDateTime now = OffsetDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.statusAt == null) {
            this.statusAt = now;
        }
    }

    @PreUpdate
    private void onUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }
}
