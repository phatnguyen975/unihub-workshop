package com.unihub.workshop.workshops.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

import com.unihub.workshop.workshops.domain.model.Speaker;
import com.unihub.workshop.workshops.domain.model.Workshop;
import com.unihub.workshop.workshops.domain.model.WorkshopStatus;
import com.unihub.workshop.workshops.infrastructure.persistence.mapper.WorkshopPersistenceMapper;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
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
class WorkshopRepositoryAdapterSpeakerTest {

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
        "save replaces speakers and returns workshop with persisted speakers"
    )
    void shouldSaveAndReplaceSpeakers() {
        UUID workshopId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        Speaker s1 = Speaker.create(
            workshopId,
            "Alice",
            "alice@example.com",
            "Bio",
            null,
            null,
            0,
            userId
        );
        Workshop workshop = Workshop.builder()
            .id(workshopId)
            .organizationId(UUID.randomUUID())
            .title("Title")
            .slug("slug")
            .timezone("Asia/Ho_Chi_Minh")
            .price(BigDecimal.ZERO)
            .currency("USD")
            .status(WorkshopStatus.DRAFT)
            .version(0)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .createdBy(userId)
            .updatedBy(userId)
            .speakers(List.of(s1))
            .build();

        WorkshopJpaEntity entity = mapper.toEntity(workshop);
        when(
            workshopJpaRepository.saveAndFlush(any(WorkshopJpaEntity.class))
        ).thenReturn(entity);

        WorkshopSpeakerJpaEntity speakerEntity = mapper.toSpeakerEntity(s1);
        when(
            speakerJpaRepository.findByWorkshopIdOrderBySortOrderAsc(workshopId)
        ).thenReturn(List.of(speakerEntity));

        Workshop result = adapter.save(workshop);

        verify(speakerJpaRepository).deleteByWorkshopId(workshopId);
        verify(speakerJpaRepository).saveAllAndFlush(anyList());
        assertThat(result.speakers()).hasSize(1);
        assertThat(result.speakers().get(0).name()).isEqualTo("Alice");
    }

    @Test
    @DisplayName("findById loads workshop with speakers")
    void shouldFindByIdWithSpeakers() {
        UUID workshopId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        Speaker s1 = Speaker.create(
            workshopId,
            "Alice",
            "alice@example.com",
            "Bio",
            null,
            null,
            0,
            userId
        );
        Workshop workshop = Workshop.builder()
            .id(workshopId)
            .organizationId(UUID.randomUUID())
            .title("Title")
            .slug("slug")
            .timezone("Asia/Ho_Chi_Minh")
            .price(BigDecimal.ZERO)
            .currency("USD")
            .status(WorkshopStatus.DRAFT)
            .version(0)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .createdBy(userId)
            .updatedBy(userId)
            .build();

        WorkshopJpaEntity entity = mapper.toEntity(workshop);
        when(workshopJpaRepository.findById(workshopId)).thenReturn(
            Optional.of(entity)
        );

        WorkshopSpeakerJpaEntity speakerEntity = mapper.toSpeakerEntity(s1);
        when(
            speakerJpaRepository.findByWorkshopIdOrderBySortOrderAsc(workshopId)
        ).thenReturn(List.of(speakerEntity));

        Optional<Workshop> result = adapter.findById(workshopId);

        assertThat(result).isPresent();
        assertThat(result.get().speakers()).hasSize(1);
        assertThat(result.get().speakers().get(0).email()).isEqualTo(
            "alice@example.com"
        );
    }

    @Test
    @DisplayName(
        "findConflictingSpeakerEmails normalizes emails and queries repository"
    )
    void shouldNormalizeEmailsAndQueryConflicts() {
        Instant startsAt = Instant.parse("2026-10-01T10:00:00Z");
        Instant endsAt = Instant.parse("2026-10-01T12:00:00Z");
        List<String> rawEmails = List.of(
            " Alice@Example.COM ",
            "bob@example.com"
        );

        when(
            speakerJpaRepository.findConflictingSpeakerEmails(
                List.of("alice@example.com", "bob@example.com"),
                startsAt,
                endsAt
            )
        ).thenReturn(List.of("alice@example.com"));

        List<String> conflicts = adapter.findConflictingSpeakerEmails(
            rawEmails,
            startsAt,
            endsAt,
            null
        );

        assertThat(conflicts).containsExactly("alice@example.com");
        verify(speakerJpaRepository).findConflictingSpeakerEmails(
            List.of("alice@example.com", "bob@example.com"),
            startsAt,
            endsAt
        );
    }

    @Test
    @DisplayName("deleteById deletes workshop speakers and workshop entity")
    void shouldDeleteByIdAndAssociatedSpeakers() {
        UUID workshopId = UUID.randomUUID();

        adapter.deleteById(workshopId);

        verify(speakerJpaRepository).deleteByWorkshopId(workshopId);
        verify(workshopJpaRepository).deleteById(workshopId);
    }
}
