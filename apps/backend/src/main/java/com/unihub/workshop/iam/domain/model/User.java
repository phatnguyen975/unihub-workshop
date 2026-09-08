package com.unihub.workshop.iam.domain.model;

import java.time.Instant;
import java.util.UUID;

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
        UUID updatedBy) {

    // Backward-compatible constructor for existing callers and tests
    public User(
            UUID id,
            UUID organizationId,
            UserRole role,
            String email,
            String passwordHash,
            boolean isActive,
            boolean isTemporaryPassword,
            Instant lastLoginAt) {
        this(id, organizationId, role, email, null, passwordHash, null, null, isActive, isTemporaryPassword,
                lastLoginAt, null, null, null, null, null);
    }

    public static User createStudent(
            UUID id,
            String email,
            String passwordHash,
            String fullName,
            UUID studentRecordId,
            UUID organizationId) {
        Instant now = Instant.now();
        return new User(
                id != null ? id : UUID.randomUUID(),
                organizationId,
                UserRole.STUDENT,
                email,
                null,
                passwordHash,
                fullName,
                null,
                false,
                false,
                null,
                studentRecordId,
                now,
                now,
                null,
                null);
    }

    public User updatePassword(String newPasswordHash) {
        return new User(
                id,
                organizationId,
                role,
                email,
                phoneNumber,
                newPasswordHash,
                fullName,
                avatarUrl,
                isActive,
                false,
                lastLoginAt,
                studentRecordId,
                createdAt,
                Instant.now(),
                createdBy,
                updatedBy);
    }

    public User changeTemporaryPassword(String newPasswordHash, Instant loginTime) {
        return new User(
                id,
                organizationId,
                role,
                email,
                phoneNumber,
                newPasswordHash,
                fullName,
                avatarUrl,
                isActive,
                false,
                loginTime,
                studentRecordId,
                createdAt,
                loginTime,
                createdBy,
                updatedBy);
    }

    public User updateProfile(String newFullName, String newPhoneNumber, String newAvatarUrl) {
        return new User(
                id,
                organizationId,
                role,
                email,
                newPhoneNumber,
                passwordHash,
                newFullName != null ? newFullName : fullName,
                newAvatarUrl,
                isActive,
                isTemporaryPassword,
                lastLoginAt,
                studentRecordId,
                createdAt,
                Instant.now(),
                createdBy,
                id);
    }
}
