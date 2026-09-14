package com.unihub.workshop.iam.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.unihub.workshop.iam.domain.model.StudentDirectory;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StudentDirectoryRepositoryAdapterTest {

    @Test
    void shouldMapEntityToDomainWhenFound() {
        StudentDirectoryJpaRepository jpaRepo = mock(
            StudentDirectoryJpaRepository.class
        );
        StudentDirectoryRepositoryAdapter adapter =
            new StudentDirectoryRepositoryAdapter(jpaRepo);

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

        when(jpaRepo.findByEmailAndStatus(email, "ACTIVE")).thenReturn(
            Optional.of(entity)
        );

        Optional<StudentDirectory> result = adapter.findActiveByEmail(email);

        assertThat(result.isPresent()).isTrue();
        StudentDirectory domain = result.get();
        assertThat(domain.id()).isEqualTo(id);
        assertThat(domain.organizationId()).isEqualTo(orgId);
        assertThat(domain.email()).isEqualTo(email);
        assertThat(domain.studentNumber()).isEqualTo("23123456");
    }

    @Test
    void shouldReturnEmptyWhenNotFound() {
        StudentDirectoryJpaRepository jpaRepo = mock(
            StudentDirectoryJpaRepository.class
        );
        StudentDirectoryRepositoryAdapter adapter =
            new StudentDirectoryRepositoryAdapter(jpaRepo);

        when(
            jpaRepo.findByEmailAndStatus("unknown@test.com", "ACTIVE")
        ).thenReturn(Optional.empty());

        Optional<StudentDirectory> result = adapter.findActiveByEmail(
            "unknown@test.com"
        );
        assertThat(result.isEmpty()).isTrue();
    }
}
