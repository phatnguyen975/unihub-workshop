package com.unihub.workshop.workshops.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.unihub.workshop.shared.events.WorkshopPublishedEvent;
import com.unihub.workshop.shared.exception.ConflictException;
import com.unihub.workshop.shared.exception.ForbiddenException;
import com.unihub.workshop.shared.exception.NotFoundException;
import com.unihub.workshop.shared.exception.ValidationException;
import com.unihub.workshop.workshops.application.ports.in.PublishWorkshopCommand;
import com.unihub.workshop.workshops.application.ports.out.WorkshopRepository;
import com.unihub.workshop.workshops.domain.model.Speaker;
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
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

class PublishWorkshopUseCaseImplTest {

    private WorkshopRepository workshopRepository;
    private ApplicationEventPublisher eventPublisher;
    private PublishWorkshopUseCaseImpl useCase;

    private static final UUID WORKSHOP_ID = UUID.randomUUID();
    private static final UUID ORG_ID = UUID.randomUUID();
    private static final UUID CALLER_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        workshopRepository = mock(WorkshopRepository.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        useCase = new PublishWorkshopUseCaseImpl(
            workshopRepository,
            eventPublisher
        );
    }

    private Workshop buildSampleDraft(UUID id, UUID organizationId) {
        Instant now = Instant.now();
        Instant startsAt = now.plusSeconds(86400 * 2);
        Instant endsAt = startsAt.plusSeconds(7200);

        Speaker speaker = Speaker.builder()
            .id(UUID.randomUUID())
            .name("Dr. Alan Turing")
            .email("alan.turing@university.edu")
            .build();

        return Workshop.builder()
            .id(id)
            .organizationId(organizationId)
            .title("Advanced Microservices")
            .slug("advanced-microservices-1234")
            .description("Deep dive into Spring Modulith")
            .coverImageUrl("https://example.com/cover.jpg")
            .location("Auditorium A")
            .timezone("Asia/Ho_Chi_Minh")
            .startsAt(startsAt)
            .endsAt(endsAt)
            .capacity(100)
            .price(BigDecimal.ZERO)
            .currency("USD")
            .status(WorkshopStatus.DRAFT)
            .version(0)
            .createdAt(now)
            .updatedAt(now)
            .createdBy(CALLER_ID)
            .updatedBy(CALLER_ID)
            .speakers(List.of(speaker))
            .build();
    }

    @Test
    @DisplayName("Successfully publish complete draft workshop")
    void shouldPublishDraftSuccessfully() {
        Workshop draft = buildSampleDraft(WORKSHOP_ID, ORG_ID);
        when(workshopRepository.findById(WORKSHOP_ID)).thenReturn(
            Optional.of(draft)
        );
        when(
            workshopRepository.findConflictingSpeakerEmails(
                any(),
                any(),
                any(),
                any()
            )
        ).thenReturn(List.of());
        when(workshopRepository.save(any(Workshop.class))).thenAnswer(
            invocation -> invocation.getArgument(0)
        );

        PublishWorkshopCommand command = PublishWorkshopCommand.builder()
            .workshopId(WORKSHOP_ID)
            .organizationId(ORG_ID)
            .callerId(CALLER_ID)
            .version(0)
            .build();

        Workshop published = useCase.execute(command);

        assertThat(published.status()).isEqualTo(WorkshopStatus.PUBLISHED);
        assertThat(published.publishedAt()).isNotNull();
        assertThat(published.updatedBy()).isEqualTo(CALLER_ID);

        verify(workshopRepository).save(any(Workshop.class));
        ArgumentCaptor<WorkshopPublishedEvent> eventCaptor =
            ArgumentCaptor.forClass(WorkshopPublishedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        WorkshopPublishedEvent event = eventCaptor.getValue();
        assertThat(event.speakers()).hasSize(1);
        assertThat(event.speakers().get(0).name()).isEqualTo("Dr. Alan Turing");
        assertThat(event.speakers().get(0).email()).isEqualTo(
            "alan.turing@university.edu"
        );
    }

    @Test
    @DisplayName("Fail to publish when workshop does not exist")
    void shouldFailWhenWorkshopNotFound() {
        when(workshopRepository.findById(WORKSHOP_ID)).thenReturn(
            Optional.empty()
        );

        PublishWorkshopCommand command = PublishWorkshopCommand.builder()
            .workshopId(WORKSHOP_ID)
            .organizationId(ORG_ID)
            .callerId(CALLER_ID)
            .version(0)
            .build();

        assertThatThrownBy(() -> useCase.execute(command))
            .isInstanceOf(NotFoundException.class)
            .hasMessageContaining(WORKSHOP_ID.toString());

        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName(
        "Fail to publish when caller organization does not match workshop organization"
    )
    void shouldFailWhenOrganizationMismatch() {
        Workshop draft = buildSampleDraft(WORKSHOP_ID, ORG_ID);
        when(workshopRepository.findById(WORKSHOP_ID)).thenReturn(
            Optional.of(draft)
        );

        PublishWorkshopCommand command = PublishWorkshopCommand.builder()
            .workshopId(WORKSHOP_ID)
            .organizationId(UUID.randomUUID()) // different org
            .callerId(CALLER_ID)
            .version(0)
            .build();

        assertThatThrownBy(() -> useCase.execute(command)).isInstanceOf(
            ForbiddenException.class
        );

        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName(
        "Fail to publish with version mismatch (optimistic locking failure)"
    )
    void shouldFailWhenVersionMismatch() {
        Workshop draft = buildSampleDraft(WORKSHOP_ID, ORG_ID);
        when(workshopRepository.findById(WORKSHOP_ID)).thenReturn(
            Optional.of(draft)
        );

        PublishWorkshopCommand command = PublishWorkshopCommand.builder()
            .workshopId(WORKSHOP_ID)
            .organizationId(ORG_ID)
            .callerId(CALLER_ID)
            .version(2) // stored is 0
            .build();

        assertThatThrownBy(() -> useCase.execute(command))
            .isInstanceOf(ConflictException.class)
            .hasMessageContaining("version conflict");

        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("Fail to publish when speaker has a schedule collision")
    void shouldFailWhenSpeakerHasConflict() {
        Workshop draft = buildSampleDraft(WORKSHOP_ID, ORG_ID);
        when(workshopRepository.findById(WORKSHOP_ID)).thenReturn(
            Optional.of(draft)
        );
        when(
            workshopRepository.findConflictingSpeakerEmails(
                any(),
                any(),
                any(),
                any()
            )
        ).thenReturn(List.of("alan.turing@university.edu"));

        PublishWorkshopCommand command = PublishWorkshopCommand.builder()
            .workshopId(WORKSHOP_ID)
            .organizationId(ORG_ID)
            .callerId(CALLER_ID)
            .version(0)
            .build();

        assertThatThrownBy(() -> useCase.execute(command))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("Speaker schedule collision detected");

        verifyNoInteractions(eventPublisher);
    }
}
