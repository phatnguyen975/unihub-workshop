package com.unihub.workshop.iam.infrastructure.persistence;

import com.unihub.workshop.iam.domain.model.StudentDirectory;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class StudentDirectoryRepositoryAdapterTest {

    @Test
    void shouldMapEntityToDomainWhenFound() {
        StudentDirectoryJpaRepository jpaRepo = mock(StudentDirectoryJpaRepository.class);
        StudentDirectoryRepositoryAdapter adapter = new StudentDirectoryRepositoryAdapter(jpaRepo);

        UUID id = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        String email = "student@test.edu.vn";
        Instant now = Instant.now();

        StudentDirectoryJpaEntity entity = new StudentDirectoryJpaEntity();
        entity.setId(id);
        entity.setOrganizationId(orgId);
        entity.setStudentNumber("23123456");
        entity.setEmail(email);
        entity.setFullName("Test Student");
        entity.setFaculty("IT");
        entity.setStatus("ACTIVE");
        entity.setImportedAt(now);
        entity.setCreatedAt(now);

        when(jpaRepo.findByEmail(email)).thenReturn(Optional.of(entity));

        Optional<StudentDirectory> result = adapter.findByEmail(email);

        assertThat(result.isPresent()).isTrue();
        StudentDirectory domain = result.get();
        assertThat(domain.id()).isEqualTo(id);
        assertThat(domain.organizationId()).isEqualTo(orgId);
        assertThat(domain.email()).isEqualTo(email);
        assertThat(domain.studentNumber()).isEqualTo("23123456");
    }

    @Test
    void shouldReturnEmptyWhenNotFound() {
        StudentDirectoryJpaRepository jpaRepo = mock(StudentDirectoryJpaRepository.class);
        StudentDirectoryRepositoryAdapter adapter = new StudentDirectoryRepositoryAdapter(jpaRepo);

        when(jpaRepo.findByEmail("unknown@test.com")).thenReturn(Optional.empty());

        Optional<StudentDirectory> result = adapter.findByEmail("unknown@test.com");
        assertThat(result.isEmpty()).isTrue();
    }
}
