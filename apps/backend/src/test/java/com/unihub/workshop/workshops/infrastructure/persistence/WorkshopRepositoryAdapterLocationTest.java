package com.unihub.workshop.workshops.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.unihub.workshop.workshops.infrastructure.persistence.mapper.WorkshopPersistenceMapper;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WorkshopRepositoryAdapterLocationTest {

    @Mock
    private WorkshopJpaRepository workshopJpaRepository;

    @Mock
    private WorkshopSpeakerJpaRepository speakerJpaRepository;

    @Mock
    private EntityManager entityManager;

    private WorkshopPersistenceMapper mapper;
    private WorkshopRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        mapper = Mappers.getMapper(WorkshopPersistenceMapper.class);
        adapter = new WorkshopRepositoryAdapter(
            workshopJpaRepository,
            speakerJpaRepository,
            mapper,
            entityManager
        );
    }

    @Test
    @DisplayName(
        "findCollidingWorkshopAtLocation normalizes location and delegates with excludeWorkshopId"
    )
    void shouldFindCollidingWorkshopAtLocationExcludingCurrentWorkshop() {
        String rawLocation = "  Auditorium A  ";
        Instant startsAt = Instant.parse("2026-12-01T10:00:00Z");
        Instant endsAt = Instant.parse("2026-12-01T12:00:00Z");
        UUID excludeWorkshopId = UUID.randomUUID();
        UUID collidingWorkshopId = UUID.randomUUID();

        when(
            workshopJpaRepository.findCollidingWorkshopIdsAtLocationExcludingWorkshop(
                "Auditorium A",
                startsAt,
                endsAt,
                excludeWorkshopId
            )
        ).thenReturn(List.of(collidingWorkshopId));

        Optional<UUID> result = adapter.findCollidingWorkshopAtLocation(
            rawLocation,
            startsAt,
            endsAt,
            excludeWorkshopId
        );

        assertThat(result).contains(collidingWorkshopId);
        verify(
            workshopJpaRepository
        ).findCollidingWorkshopIdsAtLocationExcludingWorkshop(
            "Auditorium A",
            startsAt,
            endsAt,
            excludeWorkshopId
        );
    }

    @Test
    @DisplayName(
        "findCollidingWorkshopAtLocation delegates without excludeWorkshopId when null"
    )
    void shouldFindCollidingWorkshopAtLocationWithoutExcludeWorkshopId() {
        String rawLocation = "Room 101";
        Instant startsAt = Instant.parse("2026-12-01T10:00:00Z");
        Instant endsAt = Instant.parse("2026-12-01T12:00:00Z");
        UUID collidingWorkshopId = UUID.randomUUID();

        when(
            workshopJpaRepository.findCollidingWorkshopIdsAtLocation(
                "Room 101",
                startsAt,
                endsAt
            )
        ).thenReturn(List.of(collidingWorkshopId));

        Optional<UUID> result = adapter.findCollidingWorkshopAtLocation(
            rawLocation,
            startsAt,
            endsAt,
            null
        );

        assertThat(result).contains(collidingWorkshopId);
        verify(workshopJpaRepository).findCollidingWorkshopIdsAtLocation(
            "Room 101",
            startsAt,
            endsAt
        );
    }

    @Test
    @DisplayName(
        "findCollidingWorkshopAtLocation returns empty when no collision found"
    )
    void shouldReturnEmptyWhenNoCollisionFound() {
        String location = "Auditorium B";
        Instant startsAt = Instant.parse("2026-12-01T10:00:00Z");
        Instant endsAt = Instant.parse("2026-12-01T12:00:00Z");
        UUID excludeWorkshopId = UUID.randomUUID();

        when(
            workshopJpaRepository.findCollidingWorkshopIdsAtLocationExcludingWorkshop(
                "Auditorium B",
                startsAt,
                endsAt,
                excludeWorkshopId
            )
        ).thenReturn(List.of());

        Optional<UUID> result = adapter.findCollidingWorkshopAtLocation(
            location,
            startsAt,
            endsAt,
            excludeWorkshopId
        );

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName(
        "findCollidingWorkshopAtLocation returns empty without querying repo when input invalid"
    )
    void shouldReturnEmptyWhenInputInvalid() {
        Instant startsAt = Instant.parse("2026-12-01T10:00:00Z");
        Instant endsAt = Instant.parse("2026-12-01T12:00:00Z");
        UUID excludeWorkshopId = UUID.randomUUID();

        assertThat(
            adapter.findCollidingWorkshopAtLocation(
                null,
                startsAt,
                endsAt,
                excludeWorkshopId
            )
        ).isEmpty();
        assertThat(
            adapter.findCollidingWorkshopAtLocation(
                "   ",
                startsAt,
                endsAt,
                excludeWorkshopId
            )
        ).isEmpty();
        assertThat(
            adapter.findCollidingWorkshopAtLocation(
                "Auditorium A",
                null,
                endsAt,
                excludeWorkshopId
            )
        ).isEmpty();
        assertThat(
            adapter.findCollidingWorkshopAtLocation(
                "Auditorium A",
                startsAt,
                null,
                excludeWorkshopId
            )
        ).isEmpty();

        verifyNoInteractions(workshopJpaRepository);
    }
}
