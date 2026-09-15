package com.unihub.workshop.iam.domain.model;

import java.time.Instant;
import java.util.UUID;
import lombok.Builder;

@Builder(toBuilder = true)
public record User(
    UUID id,
    UUID organizationId,
    UserRole role,
    String email,
    String phoneNumber,
    String passwordHash,
    String fullName,
    String avatarUrl,
    boolean isActive,
    boolean isTemporaryPassword,
    Instant lastLoginAt,
    UUID studentRecordId,
    Instant createdAt,
    Instant updatedAt,
    UUID createdBy,
    UUID updatedBy
) {
    public static User createStudent(
        UUID id,
        String email,
        String passwordHash,
        String fullName,
        UUID studentRecordId,
        UUID organizationId
    ) {
        Instant now = Instant.now();
        return User.builder()
            .id(id != null ? id : UUID.randomUUID())
            .organizationId(organizationId)
            .role(UserRole.STUDENT)
            .email(email)
            .passwordHash(passwordHash)
            .fullName(fullName)
            .studentRecordId(studentRecordId)
            .isActive(false)
            .isTemporaryPassword(false)
            .createdAt(now)
            .updatedAt(now)
            .build();
    }

    public static User provisionMember(
        UUID id,
        UUID organizationId,
        UserRole role,
        String email,
        String phoneNumber,
        String passwordHash,
        String fullName,
        UUID createdBy
    ) {
        Instant now = Instant.now();
        return User.builder()
            .id(id != null ? id : UUID.randomUUID())
            .organizationId(organizationId)
            .role(role)
            .email(email)
            .phoneNumber(phoneNumber)
            .passwordHash(passwordHash)
            .fullName(fullName)
            .isActive(true)
            .isTemporaryPassword(true)
            .createdAt(now)
            .updatedAt(now)
            .createdBy(createdBy)
            .updatedBy(createdBy)
            .build();
    }

    public User updatePassword(String newPasswordHash) {
        return toBuilder()
            .passwordHash(newPasswordHash)
            .isTemporaryPassword(false)
            .updatedAt(Instant.now())
            .updatedBy(id)
            .build();
    }

    public User changeTemporaryPassword(
        String newPasswordHash,
        Instant loginTime
    ) {
        return toBuilder()
            .passwordHash(newPasswordHash)
            .isTemporaryPassword(false)
            .lastLoginAt(loginTime)
            .updatedAt(loginTime)
            .build();
    }

    public User updateProfile(
        String newFullName,
        String newPhoneNumber,
        String newAvatarUrl
    ) {
        return toBuilder()
            .fullName(newFullName != null ? newFullName : fullName)
            .phoneNumber(newPhoneNumber)
            .avatarUrl(newAvatarUrl)
            .updatedAt(Instant.now())
            .updatedBy(id)
            .build();
    }

    public User updateActiveStatus(boolean newIsActive, UUID updatedBy) {
        return toBuilder()
            .isActive(newIsActive)
            .updatedAt(Instant.now())
            .updatedBy(updatedBy)
            .build();
    }
}
