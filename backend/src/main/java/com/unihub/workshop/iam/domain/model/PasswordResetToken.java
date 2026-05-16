package com.unihub.workshop.iam.domain.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "password_reset_tokens")
@Getter
@NoArgsConstructor
public class PasswordResetToken {

    @Id
    @Column(name = "id", nullable = false)
    @Setter
    private UUID id;

    @Column(name = "user_id", nullable = false)
    @Setter
    private UUID userId;

    @Column(name = "token_hash", nullable = false)
    @Setter
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    @Setter
    private OffsetDateTime expiresAt;

    @Column(name = "used_at")
    @Setter
    private OffsetDateTime usedAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
