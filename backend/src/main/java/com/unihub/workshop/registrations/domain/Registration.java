package com.unihub.workshop.registrations.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.unihub.workshop.workshops.domain.model.Workshop;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "registrations")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor
public class Registration {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    @Setter
    private UUID userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workshop_id", nullable = false)
    @JsonIgnore
    @Setter
    private Workshop workshop;

    @Column(name = "idempotency_key", nullable = false)
    @Setter
    private String idempotencyKey;

    @Column(name = "status", nullable = false)
    @Setter
    private String status = "PENDING";

    @Column(name = "ticket_type", nullable = false)
    @Setter
    private String ticketType = "FREE";

    @Column(name = "qr_code_payload")
    @Setter
    private String qrCodePayload;

    @Column(name = "qr_issued_at")
    @Setter
    private OffsetDateTime qrIssuedAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    private void ensureId() {
        if (this.id == null) this.id = UUID.randomUUID();
    }
}
