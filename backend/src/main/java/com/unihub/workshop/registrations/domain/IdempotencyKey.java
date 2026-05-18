package com.unihub.workshop.registrations.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "idempotency_keys")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor
public class IdempotencyKey {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "idempotency_key", nullable = false, unique = true)
    @Setter
    private String idempotencyKey;

    @Column(name = "request_hash")
    @Setter
    private String requestHash;

    @Column(name = "response_status_code")
    @Setter
    private Integer responseStatusCode;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "response_body", columnDefinition = "jsonb")
    @Setter
    private String responseBody;

    @Column(name = "expires_at")
    @Setter
    private OffsetDateTime expiresAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    private void ensureId() {
        if (this.id == null) this.id = UUID.randomUUID();
    }
}
