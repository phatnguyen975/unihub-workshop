package com.unihub.workshop.workshops.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.unihub.workshop.shared.exception.BadRequestException;
import com.unihub.workshop.shared.exception.ConflictException;
import com.unihub.workshop.shared.exception.ForbiddenException;
import com.unihub.workshop.shared.exception.NotFoundException;
import com.unihub.workshop.shared.storage.MediaStoragePort;
import com.unihub.workshop.workshops.application.ports.out.WorkshopRepository;
import com.unihub.workshop.workshops.domain.model.Speaker;
import com.unihub.workshop.workshops.domain.model.Workshop;
import com.unihub.workshop.workshops.domain.model.WorkshopStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;

class DeleteDraftUseCaseImplTest {

    private WorkshopRepository workshopRepository;
    private MediaStoragePort mediaStoragePort;
    private DeleteDraftUseCaseImpl useCase;

    private static final UUID WORKSHOP_ID = UUID.randomUUID();
    private static final UUID ORG_ID = UUID.randomUUID();
    private static final UUID OTHER_ORG_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        workshopRepository = mock(WorkshopRepository.class);
        mediaStoragePort = mock(MediaStoragePort.class);
        useCase = new DeleteDraftUseCaseImpl(
            workshopRepository,
            mediaStoragePort
        );
    }

    private Workshop buildWorkshop(
        UUID id,
        UUID organizationId,
        WorkshopStatus status,
        String coverImageUrl,
        List<Speaker> speakers
    ) {
        return Workshop.builder()
            .id(id)
            .organizationId(organizationId)
            .title("Title")
            .slug("title")
            .timezone("Asia/Ho_Chi_Minh")
            .price(BigDecimal.ZERO)
            .currency("USD")
            .status(status)
            .version(0)
            .coverImageUrl(coverImageUrl)
            .speakers(speakers)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();
    }

    @Test
    @DisplayName(
        "Successfully deletes DRAFT workshop and purges cover image and speaker avatars from MinIO"
    )
    void shouldDeleteDraftWorkshopAndPurgeMedia_whenValid() {
        Speaker speaker1 = Speaker.builder()
            .id(UUID.randomUUID())
            .name("Alice")
            .email("alice@example.com")
            .avatarUrl(
                "http://localhost:9000/unihub-media/speakers/avatars/alice.jpg"
            )
            .build();

        Speaker speaker2 = Speaker.builder()
            .id(UUID.randomUUID())
            .name("Bob")
            .email("bob@example.com")
            .avatarUrl(
                "http://localhost:9000/unihub-media/speakers/avatars/bob.jpg"
            )
            .build();

        Speaker speaker3 = Speaker.builder()
            .id(UUID.randomUUID())
            .name("Charlie")
            .email("charlie@example.com")
            .avatarUrl(null)
            .build();

        Workshop workshop = buildWorkshop(
            WORKSHOP_ID,
            ORG_ID,
            WorkshopStatus.DRAFT,
            "http://localhost:9000/unihub-media/workshops/covers/cover.jpg",
            List.of(speaker1, speaker2, speaker3)
        );

        when(workshopRepository.findById(WORKSHOP_ID)).thenReturn(
            Optional.of(workshop)
        );

        useCase.execute(WORKSHOP_ID, ORG_ID);

        verify(workshopRepository).deleteById(WORKSHOP_ID);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<String>> captor = ArgumentCaptor.forClass(
            Collection.class
        );
        verify(mediaStoragePort).deleteAllByUrls(captor.capture());

        Collection<String> deletedUrls = captor.getValue();
        assertThat(deletedUrls).containsExactlyInAnyOrder(
            "http://localhost:9000/unihub-media/workshops/covers/cover.jpg",
            "http://localhost:9000/unihub-media/speakers/avatars/alice.jpg",
            "http://localhost:9000/unihub-media/speakers/avatars/bob.jpg"
        );
    }

    @Test
    @DisplayName(
        "Successfully deletes DRAFT workshop without calling deleteAllByUrls when no media exists"
    )
    void shouldDeleteDraftWorkshop_whenNoMediaPresent() {
        Workshop workshop = buildWorkshop(
            WORKSHOP_ID,
            ORG_ID,
            WorkshopStatus.DRAFT,
            null,
            List.of()
        );

        when(workshopRepository.findById(WORKSHOP_ID)).thenReturn(
            Optional.of(workshop)
        );

        useCase.execute(WORKSHOP_ID, ORG_ID);

        verify(workshopRepository).deleteById(WORKSHOP_ID);
        verify(mediaStoragePort, never()).deleteAllByUrls(any());
        verify(mediaStoragePort, never()).deleteByUrl(any());
    }

    @Test
    @DisplayName(
        "Throws NotFoundException when workshop does not exist (FR-45)"
    )
    void shouldThrowNotFoundException_whenWorkshopDoesNotExist() {
        when(workshopRepository.findById(WORKSHOP_ID)).thenReturn(
            Optional.empty()
        );

        assertThatThrownBy(() -> useCase.execute(WORKSHOP_ID, ORG_ID))
            .isInstanceOf(NotFoundException.class)
            .hasMessageContaining("Workshop not found with id: " + WORKSHOP_ID);

        verify(workshopRepository, never()).deleteById(any());
        verify(mediaStoragePort, never()).deleteAllByUrls(any());
    }

    @Test
    @DisplayName(
        "Throws ForbiddenException when workshop belongs to a different organization (FR-44)"
    )
    void shouldThrowForbiddenException_whenDifferentOrganization() {
        Workshop workshop = buildWorkshop(
            WORKSHOP_ID,
            OTHER_ORG_ID,
            WorkshopStatus.DRAFT,
            null,
            null
        );

        when(workshopRepository.findById(WORKSHOP_ID)).thenReturn(
            Optional.of(workshop)
        );

        assertThatThrownBy(() -> useCase.execute(WORKSHOP_ID, ORG_ID))
            .isInstanceOf(ForbiddenException.class)
            .hasMessageContaining("permission");

        verify(workshopRepository, never()).deleteById(any());
        verify(mediaStoragePort, never()).deleteAllByUrls(any());
    }

    @ParameterizedTest
    @EnumSource(
        value = WorkshopStatus.class,
        names = { "PUBLISHED", "COMPLETED", "CANCELED" }
    )
    @DisplayName(
        "Throws ConflictException when workshop is not in DRAFT status (FR-46)"
    )
    void shouldThrowConflictException_whenStatusIsNotDraft(
        WorkshopStatus status
    ) {
        Workshop workshop = buildWorkshop(
            WORKSHOP_ID,
            ORG_ID,
            status,
            null,
            null
        );

        when(workshopRepository.findById(WORKSHOP_ID)).thenReturn(
            Optional.of(workshop)
        );

        assertThatThrownBy(() -> useCase.execute(WORKSHOP_ID, ORG_ID))
            .isInstanceOf(ConflictException.class)
            .hasMessageContaining(
                "Workshop cannot be deleted because it is not in DRAFT status."
            );

        verify(workshopRepository, never()).deleteById(any());
        verify(mediaStoragePort, never()).deleteAllByUrls(any());
    }

    @Test
    @DisplayName("Throws BadRequestException when workshopId is null")
    void shouldThrowBadRequestException_whenWorkshopIdIsNull() {
        assertThatThrownBy(() -> useCase.execute(null, ORG_ID))
            .isInstanceOf(BadRequestException.class)
            .hasMessageContaining("Workshop ID is required");
    }

    @Test
    @DisplayName("Throws BadRequestException when callerOrganizationId is null")
    void shouldThrowBadRequestException_whenCallerOrgIdIsNull() {
        assertThatThrownBy(() -> useCase.execute(WORKSHOP_ID, null))
            .isInstanceOf(BadRequestException.class)
            .hasMessageContaining("Organization ID is required");
    }
}
