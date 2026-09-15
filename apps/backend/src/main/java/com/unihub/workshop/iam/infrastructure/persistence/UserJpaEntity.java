package com.unihub.workshop.iam.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.DynamicUpdate;

@Entity
@Table(name = "users")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@DynamicUpdate
public class UserJpaEntity {

    @Id
    private UUID id;

    @Column(name = "organization_id")
    private UUID organizationId;

    @Column(name = "role", nullable = false)
    private String role;

    @Column(name = "email", nullable = false, unique = true)
    private String email;

    @Column(name = "phone_number")
    private String phoneNumber;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(name = "avatar_url")
    private String avatarUrl;

    @Column(
        name = "is_active",
        nullable = false,
        columnDefinition = "boolean default true"
    )
    private boolean isActive;

    @Column(name = "is_temporary_password", nullable = false)
    private boolean isTemporaryPassword;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "student_record_id")
    private UUID studentRecordId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "updated_by")
    private UUID updatedBy;
}
