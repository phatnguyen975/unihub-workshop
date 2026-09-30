package com.unihub.workshop.workshops.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.unihub.workshop.shared.events.WorkshopUpdatedEvent;
import com.unihub.workshop.shared.exception.ConflictException;
import com.unihub.workshop.shared.exception.ForbiddenException;
import com.unihub.workshop.shared.exception.NotFoundException;
import com.unihub.workshop.shared.exception.ValidationException;
import com.unihub.workshop.shared.storage.MediaStoragePort;
import com.unihub.workshop.workshops.application.ports.in.UpdateWorkshopCommand;
import com.unihub.workshop.workshops.application.ports.out.SlugGeneratorPort;
import com.unihub.workshop.workshops.application.ports.out.WorkshopRegistrationCountPort;
import com.unihub.workshop.workshops.application.ports.out.WorkshopRepository;
import com.unihub.workshop.workshops.domain.model.Workshop;
import com.unihub.workshop.workshops.domain.model.WorkshopStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class UpdateWorkshopUseCaseImplTest {

    private WorkshopRepository workshopRepository;
    private WorkshopRegistrationCountPort registrationCountPort;
    private SlugGeneratorPort slugGeneratorPort;
    private MediaStoragePort mediaStoragePort;
    private ApplicationEventPublisher eventPublisher;
    private UpdateWorkshopUseCaseImpl useCase;

    private static final UUID WORKSHOP_ID = UUID.randomUUID();
    private static final UUID ORG_ID = UUID.randomUUID();
    private static final UUID CALLER_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        workshopRepository = mock(WorkshopRepository.class);
        registrationCountPort = mock(WorkshopRegistrationCountPort.class);
        slugGeneratorPort = mock(SlugGeneratorPort.class);
        mediaStoragePort = mock(MediaStoragePort.class);
        eventPublisher = mock(ApplicationEventPublisher.class);

        useCase = new UpdateWorkshopUseCaseImpl(
            workshopRepository,
            registrationCountPort,
            slugGeneratorPort,
            mediaStoragePort,
            eventPublisher
        );
    }

    private Workshop buildPublishedWorkshop(
        UUID id,
        UUID organizationId,
        Integer version
    ) {
        Instant now = Instant.now();
        return Workshop.builder()
            .id(id)
            .organizationId(organizationId)
            .title("Original Title")
            .slug("original-title")
            .description("Original Desc")
            .coverImageUrl("https://example.com/old-cover.jpg")
            .location("Room 101")
            .timezone("Asia/Ho_Chi_Minh")
            .startsAt(now.plusSeconds(86400 * 2))
            .endsAt(now.plusSeconds(86400 * 2 + 7200))
            .capacity(100)
            .price(BigDecimal.ZERO)
            .currency("USD")
            .status(WorkshopStatus.PUBLISHED)
            .version(version != null ? version : 1)
            .publishedAt(now.minusSeconds(86400))
            .createdAt(now.minusSeconds(86400 * 2))
            .updatedAt(now.minusSeconds(86400))
            .createdBy(CALLER_ID)
            .updatedBy(CALLER_ID)
            .speakers(List.of())
            .build();
    }

    @Test
    @DisplayName(
        "Successfully update published workshop presentation and capacity fields"
    )
    void shouldUpdatePublishedWorkshopSuccessfully() {
        Workshop existing = buildPublishedWorkshop(WORKSHOP_ID, ORG_ID, 1);
        when(workshopRepository.findById(WORKSHOP_ID)).thenReturn(
            Optional.of(existing)
        );
        when(
            registrationCountPort.countActiveRegistrations(WORKSHOP_ID)
        ).thenReturn(50L);
        when(slugGeneratorPort.generateSlug("New Title")).thenReturn(
            "new-title-abc1"
        );
        when(workshopRepository.save(any(Workshop.class))).thenAnswer(inv ->
            inv.getArgument(0)
        );

        UpdateWorkshopCommand command = UpdateWorkshopCommand.builder()
            .workshopId(WORKSHOP_ID)
            .organizationId(ORG_ID)
            .callerId(CALLER_ID)
            .version(1)
            .title("New Title")
            .description("Updated Desc")
            .coverImageUrl("https://example.com/new-cover.jpg")
            .capacity(150)
            .build();

        Workshop result = useCase.execute(command);

        assertThat(result.title()).isEqualTo("New Title");
        assertThat(result.slug()).isEqualTo("new-title-abc1");
        assertThat(result.description()).isEqualTo("Updated Desc");
        assertThat(result.coverImageUrl()).isEqualTo(
            "https://example.com/new-cover.jpg"
        );
        assertThat(result.capacity()).isEqualTo(150);

        verify(mediaStoragePort).deleteByUrl(
            "https://example.com/old-cover.jpg"
        );
        verify(workshopRepository).save(any(Workshop.class));
        verify(eventPublisher).publishEvent(any(WorkshopUpdatedEvent.class));
    }

    @Test
    @DisplayName("Retain existing values on partial update")
    void shouldRetainExistingValuesOnPartialUpdate() {
        Workshop existing = buildPublishedWorkshop(WORKSHOP_ID, ORG_ID, 1);
        when(workshopRepository.findById(WORKSHOP_ID)).thenReturn(
            Optional.of(existing)
        );
        when(
            registrationCountPort.countActiveRegistrations(WORKSHOP_ID)
        ).thenReturn(50L);
        when(workshopRepository.save(any(Workshop.class))).thenAnswer(inv ->
            inv.getArgument(0)
        );

        UpdateWorkshopCommand command = UpdateWorkshopCommand.builder()
            .workshopId(WORKSHOP_ID)
            .organizationId(ORG_ID)
            .callerId(CALLER_ID)
            .version(1)
            .capacity(200)
            .build();

        Workshop result = useCase.execute(command);

        assertThat(result.title()).isEqualTo("Original Title");
        assertThat(result.slug()).isEqualTo("original-title");
        assertThat(result.description()).isEqualTo("Original Desc");
        assertThat(result.coverImageUrl()).isEqualTo(
            "https://example.com/old-cover.jpg"
        );
        assertThat(result.capacity()).isEqualTo(200);

        verify(mediaStoragePort, never()).deleteByUrl(any());
        verify(slugGeneratorPort, never()).generateSlug(any());
        verify(eventPublisher).publishEvent(any(WorkshopUpdatedEvent.class));
    }

    @Test
    @DisplayName("Fail to update when prohibited field is provided")
    void shouldRejectWhenProhibitedFieldProvided() {
        Workshop existing = buildPublishedWorkshop(WORKSHOP_ID, ORG_ID, 1);
        when(workshopRepository.findById(WORKSHOP_ID)).thenReturn(
            Optional.of(existing)
        );

        UpdateWorkshopCommand command = UpdateWorkshopCommand.builder()
            .workshopId(WORKSHOP_ID)
            .organizationId(ORG_ID)
            .callerId(CALLER_ID)
            .version(1)
            .title("New Title")
            .price(new BigDecimal("25.00")) // Prohibited
            .build();

        assertThatThrownBy(() -> useCase.execute(command))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("price");

        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("Fail to reduce capacity below active registration count")
    void shouldRejectCapacityReductionBelowActiveRegistrations() {
        Workshop existing = buildPublishedWorkshop(WORKSHOP_ID, ORG_ID, 1);
        when(workshopRepository.findById(WORKSHOP_ID)).thenReturn(
            Optional.of(existing)
        );
        when(
            registrationCountPort.countActiveRegistrations(WORKSHOP_ID)
        ).thenReturn(70L);

        UpdateWorkshopCommand command = UpdateWorkshopCommand.builder()
            .workshopId(WORKSHOP_ID)
            .organizationId(ORG_ID)
            .callerId(CALLER_ID)
            .version(1)
            .capacity(50) // Less than 70
            .build();

        assertThatThrownBy(() -> useCase.execute(command))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("70");

        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("Fail to update when workshop is in DRAFT status")
    void shouldRejectWhenWorkshopInDraftStatus() {
        Workshop draft = buildPublishedWorkshop(WORKSHOP_ID, ORG_ID, 0)
            .toBuilder()
            .status(WorkshopStatus.DRAFT)
            .build();
        when(workshopRepository.findById(WORKSHOP_ID)).thenReturn(
            Optional.of(draft)
        );

        UpdateWorkshopCommand command = UpdateWorkshopCommand.builder()
            .workshopId(WORKSHOP_ID)
            .organizationId(ORG_ID)
            .callerId(CALLER_ID)
            .version(0)
            .title("New Title")
            .build();

        assertThatThrownBy(() -> useCase.execute(command))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("POST /api/v1/admin/workshops/drafts");

        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("Fail to update with optimistic locking version conflict")
    void shouldRejectWhenVersionMismatch() {
        Workshop existing = buildPublishedWorkshop(WORKSHOP_ID, ORG_ID, 2);
        when(workshopRepository.findById(WORKSHOP_ID)).thenReturn(
            Optional.of(existing)
        );

        UpdateWorkshopCommand command = UpdateWorkshopCommand.builder()
            .workshopId(WORKSHOP_ID)
            .organizationId(ORG_ID)
            .callerId(CALLER_ID)
            .version(1) // Stale version
            .build();

        assertThatThrownBy(() -> useCase.execute(command))
            .isInstanceOf(ConflictException.class)
            .hasMessageContaining("version conflict");

        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName(
        "Fail to update when caller organization does not match workshop organization"
    )
    void shouldRejectWhenOrganizationMismatch() {
        Workshop existing = buildPublishedWorkshop(WORKSHOP_ID, ORG_ID, 1);
        when(workshopRepository.findById(WORKSHOP_ID)).thenReturn(
            Optional.of(existing)
        );

        UpdateWorkshopCommand command = UpdateWorkshopCommand.builder()
            .workshopId(WORKSHOP_ID)
            .organizationId(UUID.randomUUID()) // Different org
            .callerId(CALLER_ID)
            .version(1)
            .build();

        assertThatThrownBy(() -> useCase.execute(command)).isInstanceOf(
            ForbiddenException.class
        );

        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("Fail to update when workshop not found")
    void shouldRejectWhenWorkshopNotFound() {
        when(workshopRepository.findById(WORKSHOP_ID)).thenReturn(
            Optional.empty()
        );

        UpdateWorkshopCommand command = UpdateWorkshopCommand.builder()
            .workshopId(WORKSHOP_ID)
            .organizationId(ORG_ID)
            .callerId(CALLER_ID)
            .version(1)
            .build();

        assertThatThrownBy(() -> useCase.execute(command))
            .isInstanceOf(NotFoundException.class)
            .hasMessageContaining(WORKSHOP_ID.toString());

        verifyNoInteractions(eventPublisher);
    }
}
