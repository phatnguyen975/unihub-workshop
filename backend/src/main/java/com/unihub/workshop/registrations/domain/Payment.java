package com.unihub.workshop.registrations.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "payments")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor
public class Payment {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "registration_id", nullable = false)
    @Setter
    private UUID registrationId;

    @Column(name = "provider", nullable = false)
    @Setter
    private String provider;

    @Column(name = "provider_ref", nullable = false, unique = true)
    @Setter
    private String providerRef;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Setter
    private PaymentStatus status = PaymentStatus.PENDING;

    @Column(name = "amount", nullable = false)
    @Setter
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, columnDefinition = "bpchar", length = 3)
    @Setter
    private String currency;

    @Column(name = "failure_reason")
    @Setter
    private String failureReason;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "provider_payload", columnDefinition = "jsonb")
    @Setter
    private String providerPayload;

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
