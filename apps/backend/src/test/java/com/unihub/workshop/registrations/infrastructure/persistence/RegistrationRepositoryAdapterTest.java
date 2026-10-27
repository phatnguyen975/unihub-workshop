package com.unihub.workshop.registrations.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.unihub.workshop.registrations.domain.model.Registration;
import com.unihub.workshop.registrations.domain.model.RegistrationStatus;
import com.unihub.workshop.registrations.infrastructure.persistence.mapper.RegistrationPersistenceMapper;
import com.unihub.workshop.shared.exception.BadRequestException;
import com.unihub.workshop.shared.pagination.KeysetCursor;
import com.unihub.workshop.shared.pagination.KeysetCursorCodec;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RegistrationRepositoryAdapterTest {

    @Mock
    private RegistrationJpaRepository repository;

    @Mock
    private RegistrationPersistenceMapper mapper;

    @Mock
    private jakarta.persistence.EntityManager entityManager;

    private RegistrationRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new RegistrationRepositoryAdapter(
            repository,
            mapper,
            entityManager
        );
    }

    @Test
    @DisplayName(
        "save maps to entity, saves in repository, and maps back to domain"
    )
    void save_MapsAndPersists() {
        Registration domain = Registration.builder()
            .id(UUID.randomUUID())
            .build();
        RegistrationJpaEntity entity = RegistrationJpaEntity.builder()
            .id(domain.id())
            .build();
        RegistrationJpaEntity savedEntity = RegistrationJpaEntity.builder()
            .id(domain.id())
            .build();

        when(mapper.toEntity(domain)).thenReturn(entity);
        when(repository.save(entity)).thenReturn(savedEntity);
        when(mapper.toDomain(savedEntity)).thenReturn(domain);

        Registration result = adapter.save(domain);

        assertThat(result).isEqualTo(domain);
        verify(mapper).toEntity(domain);
        verify(repository).save(entity);
        verify(mapper).toDomain(savedEntity);
    }

    @Test
    @DisplayName("findById returns empty when id is null")
    void findById_NullId_ReturnsEmpty() {
        Optional<Registration> result = adapter.findById(null);

        assertThat(result).isEmpty();
        verifyNoInteractions(repository);
    }

    @Test
    @DisplayName("findById returns mapped domain when found")
    void findById_Found_ReturnsDomain() {
        UUID id = UUID.randomUUID();
        RegistrationJpaEntity entity = RegistrationJpaEntity.builder()
            .id(id)
            .build();
        Registration domain = Registration.builder().id(id).build();

        when(repository.findById(id)).thenReturn(Optional.of(entity));
        when(mapper.toDomain(entity)).thenReturn(domain);

        Optional<Registration> result = adapter.findById(id);

        assertThat(result).contains(domain);
    }

    @Test
    @DisplayName(
        "existsActiveRegistration returns false when parameters are null"
    )
    void existsActiveRegistration_NullParameters_ReturnsFalse() {
        assertThat(
            adapter.existsActiveRegistration(null, UUID.randomUUID())
        ).isFalse();
        assertThat(
            adapter.existsActiveRegistration(UUID.randomUUID(), null)
        ).isFalse();
        verifyNoInteractions(repository);
    }

    @Test
    @DisplayName("existsActiveRegistration delegates to repository")
    void existsActiveRegistration_ValidParameters_DelegatesToRepository() {
        UUID studentUserId = UUID.randomUUID();
        UUID workshopId = UUID.randomUUID();

        when(
            repository.existsActiveRegistration(studentUserId, workshopId)
        ).thenReturn(true);

        boolean exists = adapter.existsActiveRegistration(
            studentUserId,
            workshopId
        );

        assertThat(exists).isTrue();
        verify(repository).existsActiveRegistration(studentUserId, workshopId);
    }

    @Test
    @DisplayName(
        "hasOverlappingActiveRegistration returns false when parameters are null"
    )
    void hasOverlappingActiveRegistration_NullParameters_ReturnsFalse() {
        UUID studentUserId = UUID.randomUUID();
        Instant now = Instant.now();

        assertThat(
            adapter.hasOverlappingActiveRegistration(
                null,
                now,
                now.plusSeconds(3600)
            )
        ).isFalse();
        assertThat(
            adapter.hasOverlappingActiveRegistration(
                studentUserId,
                null,
                now.plusSeconds(3600)
            )
        ).isFalse();
        assertThat(
            adapter.hasOverlappingActiveRegistration(studentUserId, now, null)
        ).isFalse();
        verifyNoInteractions(repository);
    }

    @Test
    @DisplayName("hasOverlappingActiveRegistration delegates to repository")
    void hasOverlappingActiveRegistration_ValidParameters_DelegatesToRepository() {
        UUID studentUserId = UUID.randomUUID();
        Instant start = Instant.now();
        Instant end = start.plusSeconds(3600);

        when(
            repository.hasOverlappingActiveRegistration(
                studentUserId,
                start,
                end
            )
        ).thenReturn(true);

        boolean overlaps = adapter.hasOverlappingActiveRegistration(
            studentUserId,
            start,
            end
        );

        assertThat(overlaps).isTrue();
        verify(repository).hasOverlappingActiveRegistration(
            studentUserId,
            start,
            end
        );
    }

    @Test
    @DisplayName(
        "findByStudentUserId rejects a cursor issued for a different status filter"
    )
    void findByStudentUserId_CursorBoundToAnotherStatus_ThrowsBadRequest() {
        String cursor = KeysetCursorCodec.encode(
            new KeysetCursor(
                UUID.randomUUID(),
                "createdAt",
                "2026-10-21T05:12:00Z",
                null,
                "status=CONFIRMED"
            )
        );

        assertThatThrownBy(() ->
            adapter.findByStudentUserId(
                UUID.randomUUID(),
                RegistrationStatus.CANCELED,
                cursor,
                20
            )
        )
            .isInstanceOf(BadRequestException.class)
            .hasMessageContaining("different filter set");
    }
}
