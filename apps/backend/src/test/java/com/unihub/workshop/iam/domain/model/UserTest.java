package com.unihub.workshop.iam.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UserTest {

    @Test
    void createStudentShouldInitializeInactiveStudentUser() {
        UUID id = UUID.randomUUID();
        UUID studentRecordId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        String email = "student@university.edu";
        String passwordHash = "argon2hash";
        String fullName = "Alice Smith";

        User user = User.createStudent(
            id,
            email,
            passwordHash,
            fullName,
            studentRecordId,
            orgId
        );

        assertThat(user.id()).isEqualTo(id);
        assertThat(user.email()).isEqualTo(email);
        assertThat(user.passwordHash()).isEqualTo(passwordHash);
        assertThat(user.fullName()).isEqualTo(fullName);
        assertThat(user.role()).isEqualTo(UserRole.STUDENT);
        assertThat(user.isActive()).isFalse();
        assertThat(user.isTemporaryPassword()).isFalse();
        assertThat(user.studentRecordId()).isEqualTo(studentRecordId);
        assertThat(user.organizationId()).isEqualTo(orgId);
        assertThat(user.createdAt()).isNotNull();
        assertThat(user.updatedAt()).isNotNull();
    }

    @Test
    void createStudentWithoutOrgShouldAllowNullOrgAndStudentRecord() {
        User user = User.createStudent(
            null,
            "external@test.com",
            "hash",
            "External User",
            null,
            null
        );

        assertThat(user.id()).isNotNull();
        assertThat(user.organizationId()).isNull();
        assertThat(user.studentRecordId()).isNull();
        assertThat(user.isActive()).isFalse();
        assertThat(user.role()).isEqualTo(UserRole.STUDENT);
    }

    @Test
    void updateProfileShouldReturnUpdatedUserWithNewValuesAndUpdatedAt() {
        UUID id = UUID.randomUUID();
        UUID studentRecordId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        User original = User.createStudent(
            id,
            "student@university.edu",
            "hash",
            "Alice Smith",
            studentRecordId,
            orgId
        );

        User updated = original.updateProfile(
            "Alice T. Smith",
            "+84912345678",
            "https://cdn.unihub.vn/avatar.jpg"
        );

        assertThat(updated.id()).isEqualTo(original.id());
        assertThat(updated.email()).isEqualTo(original.email());
        assertThat(updated.passwordHash()).isEqualTo(original.passwordHash());
        assertThat(updated.role()).isEqualTo(original.role());
        assertThat(updated.organizationId()).isEqualTo(
            original.organizationId()
        );
        assertThat(updated.studentRecordId()).isEqualTo(
            original.studentRecordId()
        );
        assertThat(updated.createdAt()).isEqualTo(original.createdAt());

        assertThat(updated.fullName()).isEqualTo("Alice T. Smith");
        assertThat(updated.phoneNumber()).isEqualTo("+84912345678");
        assertThat(updated.avatarUrl()).isEqualTo(
            "https://cdn.unihub.vn/avatar.jpg"
        );
        assertThat(updated.updatedAt()).isNotNull();
        assertThat(updated.updatedBy()).isEqualTo(original.id());
    }

    @Test
    void updateProfileShouldAllowNullPhoneNumberAndAvatarUrl() {
        UUID id = UUID.randomUUID();
        User original = new User(
            id,
            null,
            UserRole.STUDENT,
            "student@university.edu",
            "+84912345678",
            "hash",
            "Alice Smith",
            "https://cdn.unihub.vn/avatar.jpg",
            true,
            false,
            null,
            null,
            null,
            null,
            null,
            null
        );

        User updated = original.updateProfile("Alice Smith", null, null);

        assertThat(updated.fullName()).isEqualTo("Alice Smith");
        assertThat(updated.phoneNumber()).isNull();
        assertThat(updated.avatarUrl()).isNull();
    }

    @Test
    void updatePasswordShouldUpdatePasswordHashAndAuditFields() {
        UUID id = UUID.randomUUID();
        UUID creatorId = UUID.randomUUID();
        User original = new User(
            id,
            null,
            UserRole.STUDENT,
            "student@university.edu",
            "+84912345678",
            "old-hash",
            "Alice Smith",
            null,
            true,
            false,
            null,
            null,
            Instant.now().minusSeconds(3600),
            Instant.now().minusSeconds(3600),
            creatorId,
            creatorId
        );

        User updated = original.updatePassword("new-argon2-hash");

        assertThat(updated.passwordHash()).isEqualTo("new-argon2-hash");
        assertThat(updated.updatedAt()).isNotNull();
        assertThat(updated.updatedBy()).isEqualTo(id);
    }
}
