package com.unihub.workshop.iam.domain.model;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UserTest {

    @Test
    void createStudentShouldInitializeInactiveStudentUser() {
        UUID id = UUID.randomUUID();
        UUID studentRecordId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        String email = "student@university.edu";
        String passwordHash = "argon2hash";
        String fullName = "Alice Smith";

        User user = User.createStudent(id, email, passwordHash, fullName, studentRecordId, orgId);

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
        User user = User.createStudent(null, "external@test.com", "hash", "External User", null, null);

        assertThat(user.id()).isNotNull();
        assertThat(user.organizationId()).isNull();
        assertThat(user.studentRecordId()).isNull();
        assertThat(user.isActive()).isFalse();
        assertThat(user.role()).isEqualTo(UserRole.STUDENT);
    }
}
