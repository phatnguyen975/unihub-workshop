package com.unihub.workshop.workshops.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.unihub.workshop.shared.events.WorkshopRescheduledEvent;
import com.unihub.workshop.shared.exception.ConflictException;
import com.unihub.workshop.shared.exception.ForbiddenException;
import com.unihub.workshop.shared.exception.NotFoundException;
import com.unihub.workshop.shared.exception.ValidationException;
import com.unihub.workshop.workshops.application.ports.in.RescheduleWorkshopCommand;
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

class RescheduleWorkshopUseCaseImplTest {

    private WorkshopRepository workshopRepository;
    private ApplicationEventPublisher eventPublisher;
    private RescheduleWorkshopUseCaseImpl useCase;

    private static final UUID WORKSHOP_ID = UUID.randomUUID();
    private static final UUID ORG_ID = UUID.randomUUID();
    private static final UUID CALLER_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        workshopRepository = mock(WorkshopRepository.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        useCase = new RescheduleWorkshopUseCaseImpl(
            workshopRepository,
            eventPublisher
        );
    }

    private Workshop buildPublishedWorkshop(
        UUID id,
        UUID organizationId,
        int version
    ) {
        Instant startsAt = Instant.now().plusSeconds(86400 * 2);
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
            .slug("advanced-microservices-a1b2")
            .description("Deep dive")
            .coverImageUrl("https://example.com/cover.jpg")
            .location("Auditorium A, Building C")
            .timezone("Asia/Ho_Chi_Minh")
            .startsAt(startsAt)
            .endsAt(endsAt)
            .capacity(100)
            .price(BigDecimal.ZERO)
            .currency("USD")
            .status(WorkshopStatus.PUBLISHED)
            .version(version)
            .speakers(List.of(speaker))
            .publishedAt(Instant.now().minusSeconds(86400))
            .createdAt(Instant.now().minusSeconds(86400 * 2))
            .updatedAt(Instant.now().minusSeconds(86400))
            .createdBy(CALLER_ID)
            .updatedBy(CALLER_ID)
            .build();
    }

    @Test
    @DisplayName(
        "execute successfully reschedules workshop and publishes WorkshopRescheduledEvent"
    )
    void shouldRescheduleWorkshopSuccessfullyAndEmitEvent() {
        Workshop existing = buildPublishedWorkshop(WORKSHOP_ID, ORG_ID, 2);
        Instant originalStartsAt = existing.startsAt();
        Instant originalEndsAt = existing.endsAt();

        Instant newStartsAt = Instant.now().plusSeconds(86400 * 5);
        Instant newEndsAt = newStartsAt.plusSeconds(10800);

        RescheduleWorkshopCommand command = RescheduleWorkshopCommand.builder()
            .workshopId(WORKSHOP_ID)
            .organizationId(ORG_ID)
            .callerId(CALLER_ID)
            .version(2)
            .startsAt(newStartsAt)
            .endsAt(newEndsAt)
            .build();

        when(workshopRepository.findById(WORKSHOP_ID)).thenReturn(
            Optional.of(existing)
        );
        when(
            workshopRepository.findConflictingSpeakerEmails(
                any(),
                eq(newStartsAt),
                eq(newEndsAt),
                eq(WORKSHOP_ID)
            )
        ).thenReturn(List.of());
        when(
            workshopRepository.findCollidingWorkshopAtLocation(
                existing.location(),
                newStartsAt,
                newEndsAt,
                WORKSHOP_ID
            )
        ).thenReturn(Optional.empty());
        when(workshopRepository.save(any(Workshop.class))).thenAnswer(
            invocation -> invocation.getArgument(0)
        );

        Workshop result = useCase.execute(command);

        assertThat(result.startsAt()).isEqualTo(newStartsAt);
        assertThat(result.endsAt()).isEqualTo(newEndsAt);
        assertThat(result.updatedBy()).isEqualTo(CALLER_ID);

        verify(workshopRepository).save(any(Workshop.class));

        ArgumentCaptor<WorkshopRescheduledEvent> eventCaptor =
            ArgumentCaptor.forClass(WorkshopRescheduledEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());

        WorkshopRescheduledEvent event = eventCaptor.getValue();
        assertThat(event.workshopId()).isEqualTo(WORKSHOP_ID);
        assertThat(event.organizationId()).isEqualTo(ORG_ID);
        assertThat(event.title()).isEqualTo(existing.title());
        assertThat(event.slug()).isEqualTo(existing.slug());
        assertThat(event.location()).isEqualTo(existing.location());
        assertThat(event.timezone()).isEqualTo(existing.timezone());
        assertThat(event.previousStartsAt()).isEqualTo(originalStartsAt);
        assertThat(event.previousEndsAt()).isEqualTo(originalEndsAt);
        assertThat(event.newStartsAt()).isEqualTo(newStartsAt);
        assertThat(event.newEndsAt()).isEqualTo(newEndsAt);
        assertThat(event.occurredAt()).isNotNull();
    }

    @Test
    @DisplayName("execute throws ValidationException when version is null")
    void shouldFailWhenVersionNull() {
        RescheduleWorkshopCommand command = RescheduleWorkshopCommand.builder()
            .workshopId(WORKSHOP_ID)
            .organizationId(ORG_ID)
            .callerId(CALLER_ID)
            .version(null)
            .startsAt(Instant.now().plusSeconds(86400 * 3))
            .endsAt(Instant.now().plusSeconds(86400 * 3 + 7200))
            .build();

        assertThatThrownBy(() -> useCase.execute(command))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("must not be null")
            .satisfies(ex -> {
                ValidationException ve = (ValidationException) ex;
                assertThat(ve.getValidationErrors()).containsExactly(
                    "version: must not be null"
                );
            });

        verifyNoInteractions(workshopRepository);
    }

    @Test
    @DisplayName(
        "execute throws ValidationException when prohibited field is provided"
    )
    void shouldFailWhenProhibitedFieldPresent() {
        RescheduleWorkshopCommand command = RescheduleWorkshopCommand.builder()
            .workshopId(WORKSHOP_ID)
            .organizationId(ORG_ID)
            .callerId(CALLER_ID)
            .version(1)
            .startsAt(Instant.now().plusSeconds(86400 * 3))
            .endsAt(Instant.now().plusSeconds(86400 * 3 + 7200))
            .location("New Auditorium")
            .build();

        assertThatThrownBy(() -> useCase.execute(command))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("location");

        verifyNoInteractions(workshopRepository);
    }

    @Test
    @DisplayName(
        "execute throws NotFoundException when workshop does not exist"
    )
    void shouldFailWhenWorkshopNotFound() {
        Instant newStartsAt = Instant.now().plusSeconds(86400 * 3);
        Instant newEndsAt = newStartsAt.plusSeconds(7200);

        RescheduleWorkshopCommand command = RescheduleWorkshopCommand.builder()
            .workshopId(WORKSHOP_ID)
            .organizationId(ORG_ID)
            .callerId(CALLER_ID)
            .version(1)
            .startsAt(newStartsAt)
            .endsAt(newEndsAt)
            .build();

        when(workshopRepository.findById(WORKSHOP_ID)).thenReturn(
            Optional.empty()
        );

        assertThatThrownBy(() -> useCase.execute(command))
            .isInstanceOf(NotFoundException.class)
            .hasMessageContaining("not found");
    }

    @Test
    @DisplayName(
        "execute throws ForbiddenException when caller org does not match workshop org"
    )
    void shouldFailWhenCrossTenantAttempt() {
        Workshop existing = buildPublishedWorkshop(WORKSHOP_ID, ORG_ID, 1);
        UUID differentOrgId = UUID.randomUUID();

        Instant newStartsAt = Instant.now().plusSeconds(86400 * 3);
        Instant newEndsAt = newStartsAt.plusSeconds(7200);

        RescheduleWorkshopCommand command = RescheduleWorkshopCommand.builder()
            .workshopId(WORKSHOP_ID)
            .organizationId(differentOrgId)
            .callerId(CALLER_ID)
            .version(1)
            .startsAt(newStartsAt)
            .endsAt(newEndsAt)
            .build();

        when(workshopRepository.findById(WORKSHOP_ID)).thenReturn(
            Optional.of(existing)
        );

        assertThatThrownBy(() -> useCase.execute(command)).isInstanceOf(
            ForbiddenException.class
        );
    }

    @Test
    @DisplayName(
        "execute throws ValidationException when workshop is in DRAFT status"
    )
    void shouldFailWhenStatusIsDraft() {
        Workshop draft = buildPublishedWorkshop(WORKSHOP_ID, ORG_ID, 0)
            .toBuilder()
            .status(WorkshopStatus.DRAFT)
            .build();

        Instant newStartsAt = Instant.now().plusSeconds(86400 * 3);
        Instant newEndsAt = newStartsAt.plusSeconds(7200);

        RescheduleWorkshopCommand command = RescheduleWorkshopCommand.builder()
            .workshopId(WORKSHOP_ID)
            .organizationId(ORG_ID)
            .callerId(CALLER_ID)
            .version(0)
            .startsAt(newStartsAt)
            .endsAt(newEndsAt)
            .build();

        when(workshopRepository.findById(WORKSHOP_ID)).thenReturn(
            Optional.of(draft)
        );

        assertThatThrownBy(() -> useCase.execute(command))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("DRAFT status");
    }

    @Test
    @DisplayName(
        "execute throws ValidationException when workshop is in terminal status"
    )
    void shouldFailWhenStatusIsCompletedOrCanceled() {
        Workshop completed = buildPublishedWorkshop(WORKSHOP_ID, ORG_ID, 3)
            .toBuilder()
            .status(WorkshopStatus.COMPLETED)
            .build();

        Instant newStartsAt = Instant.now().plusSeconds(86400 * 3);
        Instant newEndsAt = newStartsAt.plusSeconds(7200);

        RescheduleWorkshopCommand command = RescheduleWorkshopCommand.builder()
            .workshopId(WORKSHOP_ID)
            .organizationId(ORG_ID)
            .callerId(CALLER_ID)
            .version(3)
            .startsAt(newStartsAt)
            .endsAt(newEndsAt)
            .build();

        when(workshopRepository.findById(WORKSHOP_ID)).thenReturn(
            Optional.of(completed)
        );

        assertThatThrownBy(() -> useCase.execute(command))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("terminal status");
    }

    @Test
    @DisplayName("execute throws ConflictException when version does not match")
    void shouldFailWhenVersionMismatch() {
        Workshop existing = buildPublishedWorkshop(WORKSHOP_ID, ORG_ID, 2);

        Instant newStartsAt = Instant.now().plusSeconds(86400 * 3);
        Instant newEndsAt = newStartsAt.plusSeconds(7200);

        RescheduleWorkshopCommand command = RescheduleWorkshopCommand.builder()
            .workshopId(WORKSHOP_ID)
            .organizationId(ORG_ID)
            .callerId(CALLER_ID)
            .version(1)
            .startsAt(newStartsAt)
            .endsAt(newEndsAt)
            .build();

        when(workshopRepository.findById(WORKSHOP_ID)).thenReturn(
            Optional.of(existing)
        );

        assertThatThrownBy(() -> useCase.execute(command))
            .isInstanceOf(ConflictException.class)
            .hasMessageContaining("conflict");
    }

    @Test
    @DisplayName(
        "execute throws ValidationException when startsAt is in the past"
    )
    void shouldFailWhenStartsAtInPast() {
        Workshop existing = buildPublishedWorkshop(WORKSHOP_ID, ORG_ID, 2);

        Instant pastStartsAt = Instant.now().minusSeconds(3600);
        Instant newEndsAt = Instant.now().plusSeconds(3600);

        RescheduleWorkshopCommand command = RescheduleWorkshopCommand.builder()
            .workshopId(WORKSHOP_ID)
            .organizationId(ORG_ID)
            .callerId(CALLER_ID)
            .version(2)
            .startsAt(pastStartsAt)
            .endsAt(newEndsAt)
            .build();

        when(workshopRepository.findById(WORKSHOP_ID)).thenReturn(
            Optional.of(existing)
        );

        assertThatThrownBy(() -> useCase.execute(command))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("future")
            .satisfies(ex -> {
                ValidationException ve = (ValidationException) ex;
                assertThat(ve.getValidationErrors()).containsExactly(
                    "startsAt: Workshop start time must be in the future"
                );
            });
    }

    @Test
    @DisplayName(
        "execute throws ValidationException when endsAt is not strictly after startsAt"
    )
    void shouldFailWhenEndsAtNotAfterStartsAt() {
        Workshop existing = buildPublishedWorkshop(WORKSHOP_ID, ORG_ID, 2);

        Instant newStartsAt = Instant.now().plusSeconds(86400 * 3);

        RescheduleWorkshopCommand command = RescheduleWorkshopCommand.builder()
            .workshopId(WORKSHOP_ID)
            .organizationId(ORG_ID)
            .callerId(CALLER_ID)
            .version(2)
            .startsAt(newStartsAt)
            .endsAt(newStartsAt)
            .build();

        when(workshopRepository.findById(WORKSHOP_ID)).thenReturn(
            Optional.of(existing)
        );

        assertThatThrownBy(() -> useCase.execute(command))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("strictly after")
            .satisfies(ex -> {
                ValidationException ve = (ValidationException) ex;
                assertThat(ve.getValidationErrors()).containsExactly(
                    "endsAt: endsAt must be strictly after startsAt"
                );
            });
    }

    @Test
    @DisplayName(
        "execute throws ValidationException when startsAt or endsAt is null"
    )
    void shouldFailWhenStartsAtOrEndsAtNull() {
        RescheduleWorkshopCommand missingStartsAt =
            RescheduleWorkshopCommand.builder()
                .workshopId(WORKSHOP_ID)
                .organizationId(ORG_ID)
                .callerId(CALLER_ID)
                .version(2)
                .startsAt(null)
                .endsAt(Instant.now().plusSeconds(86400 * 3 + 7200))
                .build();

        assertThatThrownBy(() -> useCase.execute(missingStartsAt))
            .isInstanceOf(ValidationException.class)
            .satisfies(ex -> {
                ValidationException ve = (ValidationException) ex;
                assertThat(ve.getValidationErrors()).containsExactly(
                    "startsAt: must not be null"
                );
            });

        RescheduleWorkshopCommand missingEndsAt =
            RescheduleWorkshopCommand.builder()
                .workshopId(WORKSHOP_ID)
                .organizationId(ORG_ID)
                .callerId(CALLER_ID)
                .version(2)
                .startsAt(Instant.now().plusSeconds(86400 * 3))
                .endsAt(null)
                .build();

        assertThatThrownBy(() -> useCase.execute(missingEndsAt))
            .isInstanceOf(ValidationException.class)
            .satisfies(ex -> {
                ValidationException ve = (ValidationException) ex;
                assertThat(ve.getValidationErrors()).containsExactly(
                    "endsAt: must not be null"
                );
            });
    }

    @Test
    @DisplayName(
        "execute throws ValidationException when speaker schedule collision detected in new window"
    )
    void shouldFailWhenSpeakerCollisionDetected() {
        Workshop existing = buildPublishedWorkshop(WORKSHOP_ID, ORG_ID, 2);

        Instant newStartsAt = Instant.now().plusSeconds(86400 * 3);
        Instant newEndsAt = newStartsAt.plusSeconds(7200);

        RescheduleWorkshopCommand command = RescheduleWorkshopCommand.builder()
            .workshopId(WORKSHOP_ID)
            .organizationId(ORG_ID)
            .callerId(CALLER_ID)
            .version(2)
            .startsAt(newStartsAt)
            .endsAt(newEndsAt)
            .build();

        when(workshopRepository.findById(WORKSHOP_ID)).thenReturn(
            Optional.of(existing)
        );
        when(
            workshopRepository.findConflictingSpeakerEmails(
                any(),
                eq(newStartsAt),
                eq(newEndsAt),
                eq(WORKSHOP_ID)
            )
        ).thenReturn(List.of("alan.turing@university.edu"));

        assertThatThrownBy(() -> useCase.execute(command))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("Speaker schedule collision")
            .hasMessageContaining("alan.turing@university.edu");

        verify(workshopRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName(
        "execute throws ValidationException when location collision detected in new window"
    )
    void shouldFailWhenLocationCollisionDetected() {
        Workshop existing = buildPublishedWorkshop(WORKSHOP_ID, ORG_ID, 2);

        Instant newStartsAt = Instant.now().plusSeconds(86400 * 3);
        Instant newEndsAt = newStartsAt.plusSeconds(7200);

        RescheduleWorkshopCommand command = RescheduleWorkshopCommand.builder()
            .workshopId(WORKSHOP_ID)
            .organizationId(ORG_ID)
            .callerId(CALLER_ID)
            .version(2)
            .startsAt(newStartsAt)
            .endsAt(newEndsAt)
            .build();

        when(workshopRepository.findById(WORKSHOP_ID)).thenReturn(
            Optional.of(existing)
        );
        when(
            workshopRepository.findConflictingSpeakerEmails(
                any(),
                eq(newStartsAt),
                eq(newEndsAt),
                eq(WORKSHOP_ID)
            )
        ).thenReturn(List.of());
        when(
            workshopRepository.findCollidingWorkshopAtLocation(
                existing.location(),
                newStartsAt,
                newEndsAt,
                WORKSHOP_ID
            )
        ).thenReturn(Optional.of(UUID.randomUUID()));

        assertThatThrownBy(() -> useCase.execute(command))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("Location collision");

        verify(workshopRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }
}
