package com.unihub.workshop.iam.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.unihub.workshop.iam.domain.model.StudentDirectory;
import com.unihub.workshop.iam.infrastructure.persistence.mapper.StudentDirectoryPersistenceMapper;
import com.unihub.workshop.iam.infrastructure.persistence.mapper.StudentDirectoryPersistenceMapperImpl;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StudentDirectoryRepositoryAdapterTest {

    private final StudentDirectoryPersistenceMapper mapper =
        new StudentDirectoryPersistenceMapperImpl();

    @Test
    void shouldMapEntityToDomainWhenFound() {
        StudentDirectoryJpaRepository jpaRepo = mock(
            StudentDirectoryJpaRepository.class
        );
        StudentDirectoryRepositoryAdapter adapter =
            new StudentDirectoryRepositoryAdapter(jpaRepo, mapper);

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
            new StudentDirectoryRepositoryAdapter(jpaRepo, mapper);

        when(
            jpaRepo.findByEmailAndStatus("unknown@test.com", "ACTIVE")
        ).thenReturn(Optional.empty());

        Optional<StudentDirectory> result = adapter.findActiveByEmail(
            "unknown@test.com"
        );
        assertThat(result.isEmpty()).isTrue();
    }

    @Test
    void shouldMapAllEntitiesForBatchLookup() {
        StudentDirectoryJpaRepository jpaRepo = mock(
            StudentDirectoryJpaRepository.class
        );
        StudentDirectoryRepositoryAdapter adapter =
            new StudentDirectoryRepositoryAdapter(jpaRepo, mapper);

        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        Set<UUID> ids = Set.of(firstId, secondId);

        when(jpaRepo.findAllById(ids)).thenReturn(
            List.of(
                directoryEntity(firstId, "23123456"),
                directoryEntity(secondId, "23999999")
            )
        );

        List<StudentDirectory> result = adapter.findAllByIds(ids);

        assertThat(result)
            .extracting(StudentDirectory::id)
            .containsExactly(firstId, secondId);
        assertThat(result)
            .extracting(StudentDirectory::studentNumber)
            .containsExactly("23123456", "23999999");
    }

    @Test
    void shouldReturnEmptyListWithoutQueryingWhenBatchIdsAreEmpty() {
        StudentDirectoryJpaRepository jpaRepo = mock(
            StudentDirectoryJpaRepository.class
        );
        StudentDirectoryRepositoryAdapter adapter =
            new StudentDirectoryRepositoryAdapter(jpaRepo, mapper);

        assertThat(adapter.findAllByIds(Set.of())).isEmpty();
        assertThat(adapter.findAllByIds(null)).isEmpty();

        verifyNoInteractions(jpaRepo);
    }

    private StudentDirectoryJpaEntity directoryEntity(
        UUID id,
        String studentNumber
    ) {
        Instant now = Instant.now();
        return StudentDirectoryJpaEntity.builder()
            .id(id)
            .organizationId(UUID.randomUUID())
            .studentNumber(studentNumber)
            .email(studentNumber + "@student.university.edu")
            .fullName("Student " + studentNumber)
            .status("ACTIVE")
            .importedAt(now)
            .createdAt(now)
            .updatedAt(now)
            .build();
    }
}
