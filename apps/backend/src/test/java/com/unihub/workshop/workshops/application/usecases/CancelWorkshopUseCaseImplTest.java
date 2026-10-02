package com.unihub.workshop.workshops.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.unihub.workshop.shared.events.WorkshopCanceledEvent;
import com.unihub.workshop.shared.exception.ConflictException;
import com.unihub.workshop.shared.exception.ForbiddenException;
import com.unihub.workshop.shared.exception.NotFoundException;
import com.unihub.workshop.shared.exception.ValidationException;
import com.unihub.workshop.workshops.application.ports.in.CancelWorkshopCommand;
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
import org.springframework.dao.OptimisticLockingFailureException;

class CancelWorkshopUseCaseImplTest {

    private WorkshopRepository workshopRepository;
    private ApplicationEventPublisher eventPublisher;
    private CancelWorkshopUseCaseImpl useCase;

    private static final UUID WORKSHOP_ID = UUID.randomUUID();
    private static final UUID ORG_ID = UUID.randomUUID();
    private static final UUID CALLER_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        workshopRepository = mock(WorkshopRepository.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        useCase = new CancelWorkshopUseCaseImpl(
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
        "Successfully cancel a published workshop with reason and publish WorkshopCanceledEvent"
    )
    void shouldCancelPublishedWorkshopSuccessfully_withReason() {
        Workshop existing = buildPublishedWorkshop(WORKSHOP_ID, ORG_ID, 2);
        when(workshopRepository.findById(WORKSHOP_ID)).thenReturn(
            Optional.of(existing)
        );
        when(workshopRepository.save(any(Workshop.class))).thenAnswer(
            invocation -> invocation.getArgument(0)
        );

        CancelWorkshopCommand command = CancelWorkshopCommand.builder()
            .workshopId(WORKSHOP_ID)
            .organizationId(ORG_ID)
            .callerId(CALLER_ID)
            .version(2)
            .reason("Venue is unavailable due to unforeseen circumstances.")
            .build();

        Workshop result = useCase.execute(command);

        assertThat(result.status()).isEqualTo(WorkshopStatus.CANCELED);
        assertThat(result.canceledAt()).isNotNull();
        assertThat(result.updatedBy()).isEqualTo(CALLER_ID);

        verify(workshopRepository).save(any(Workshop.class));

        ArgumentCaptor<WorkshopCanceledEvent> eventCaptor =
            ArgumentCaptor.forClass(WorkshopCanceledEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());

        WorkshopCanceledEvent event = eventCaptor.getValue();
        assertThat(event.workshopId()).isEqualTo(WORKSHOP_ID);
        assertThat(event.organizationId()).isEqualTo(ORG_ID);
        assertThat(event.title()).isEqualTo(existing.title());
        assertThat(event.slug()).isEqualTo(existing.slug());
        assertThat(event.startsAt()).isEqualTo(existing.startsAt());
        assertThat(event.endsAt()).isEqualTo(existing.endsAt());
        assertThat(event.canceledAt()).isEqualTo(result.canceledAt());
        assertThat(event.reason()).isEqualTo(
            "Venue is unavailable due to unforeseen circumstances."
        );
        assertThat(event.occurredAt()).isNotNull();
    }

    @Test
    @DisplayName("Successfully cancel a published workshop without reason")
    void shouldCancelPublishedWorkshopSuccessfully_withoutReason() {
        Workshop existing = buildPublishedWorkshop(WORKSHOP_ID, ORG_ID, 1);
        when(workshopRepository.findById(WORKSHOP_ID)).thenReturn(
            Optional.of(existing)
        );
        when(workshopRepository.save(any(Workshop.class))).thenAnswer(
            invocation -> invocation.getArgument(0)
        );

        CancelWorkshopCommand command = CancelWorkshopCommand.builder()
            .workshopId(WORKSHOP_ID)
            .organizationId(ORG_ID)
            .callerId(CALLER_ID)
            .version(1)
            .build();

        Workshop result = useCase.execute(command);

        assertThat(result.status()).isEqualTo(WorkshopStatus.CANCELED);
        assertThat(result.canceledAt()).isNotNull();

        ArgumentCaptor<WorkshopCanceledEvent> eventCaptor =
            ArgumentCaptor.forClass(WorkshopCanceledEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().reason()).isNull();
    }

    @Test
    @DisplayName("Normalize blank reason to null in event payload")
    void shouldNormalizeBlankReasonToNull() {
        Workshop existing = buildPublishedWorkshop(WORKSHOP_ID, ORG_ID, 1);
        when(workshopRepository.findById(WORKSHOP_ID)).thenReturn(
            Optional.of(existing)
        );
        when(workshopRepository.save(any(Workshop.class))).thenAnswer(
            invocation -> invocation.getArgument(0)
        );

        CancelWorkshopCommand command = CancelWorkshopCommand.builder()
            .workshopId(WORKSHOP_ID)
            .organizationId(ORG_ID)
            .callerId(CALLER_ID)
            .version(1)
            .reason("    ")
            .build();

        useCase.execute(command);

        ArgumentCaptor<WorkshopCanceledEvent> eventCaptor =
            ArgumentCaptor.forClass(WorkshopCanceledEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().reason()).isNull();
    }

    @Test
    @DisplayName("Fail when version is null")
    void shouldFailWhenVersionIsNull() {
        CancelWorkshopCommand command = CancelWorkshopCommand.builder()
            .workshopId(WORKSHOP_ID)
            .organizationId(ORG_ID)
            .callerId(CALLER_ID)
            .version(null)
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
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("Fail when reason exceeds 1000 characters after trimming")
    void shouldFailWhenReasonExceeds1000Chars() {
        String tooLongReason = "a".repeat(1001);
        CancelWorkshopCommand command = CancelWorkshopCommand.builder()
            .workshopId(WORKSHOP_ID)
            .organizationId(ORG_ID)
            .callerId(CALLER_ID)
            .version(1)
            .reason(tooLongReason)
            .build();

        assertThatThrownBy(() -> useCase.execute(command))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("1000")
            .satisfies(ex -> {
                ValidationException ve = (ValidationException) ex;
                assertThat(ve.getValidationErrors()).containsExactly(
                    "reason: Must not exceed 1000 characters after trimming"
                );
            });

        verifyNoInteractions(workshopRepository);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("Fail when prohibited fields are present in the command")
    void shouldFailWhenProhibitedFieldsPresent() {
        CancelWorkshopCommand command = CancelWorkshopCommand.builder()
            .workshopId(WORKSHOP_ID)
            .organizationId(ORG_ID)
            .callerId(CALLER_ID)
            .version(1)
            .title("Prohibited Title")
            .timezone("Asia/Tokyo")
            .build();

        assertThatThrownBy(() -> useCase.execute(command))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("cannot be modified via the cancel endpoint")
            .satisfies(ex -> {
                ValidationException ve = (ValidationException) ex;
                assertThat(ve.getValidationErrors()).anyMatch(err ->
                    err.startsWith("title:")
                );
                assertThat(ve.getValidationErrors()).anyMatch(err ->
                    err.startsWith("timezone:")
                );
            });

        verifyNoInteractions(workshopRepository);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("Fail when workshop does not exist")
    void shouldFailWhenWorkshopNotFound() {
        when(workshopRepository.findById(WORKSHOP_ID)).thenReturn(
            Optional.empty()
        );

        CancelWorkshopCommand command = CancelWorkshopCommand.builder()
            .workshopId(WORKSHOP_ID)
            .organizationId(ORG_ID)
            .callerId(CALLER_ID)
            .version(1)
            .build();

        assertThatThrownBy(() -> useCase.execute(command))
            .isInstanceOf(NotFoundException.class)
            .hasMessageContaining("Workshop not found");

        verify(workshopRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName(
        "Fail when caller organization does not match workshop organization"
    )
    void shouldFailWhenOrganizationMismatch() {
        UUID otherOrgId = UUID.randomUUID();
        Workshop existing = buildPublishedWorkshop(WORKSHOP_ID, otherOrgId, 1);
        when(workshopRepository.findById(WORKSHOP_ID)).thenReturn(
            Optional.of(existing)
        );

        CancelWorkshopCommand command = CancelWorkshopCommand.builder()
            .workshopId(WORKSHOP_ID)
            .organizationId(ORG_ID)
            .callerId(CALLER_ID)
            .version(1)
            .build();

        assertThatThrownBy(() -> useCase.execute(command))
            .isInstanceOf(ForbiddenException.class)
            .hasMessageContaining(
                "You do not have permission to cancel workshops for another organization."
            );

        verify(workshopRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("Fail when version does not match stored version")
    void shouldFailWhenVersionMismatch() {
        Workshop existing = buildPublishedWorkshop(WORKSHOP_ID, ORG_ID, 2);
        when(workshopRepository.findById(WORKSHOP_ID)).thenReturn(
            Optional.of(existing)
        );

        CancelWorkshopCommand command = CancelWorkshopCommand.builder()
            .workshopId(WORKSHOP_ID)
            .organizationId(ORG_ID)
            .callerId(CALLER_ID)
            .version(1)
            .build();

        assertThatThrownBy(() -> useCase.execute(command))
            .isInstanceOf(ConflictException.class)
            .hasMessageContaining("expected version 1 but found version 2");

        verify(workshopRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("Fail when workshop is in DRAFT status")
    void shouldFailWhenWorkshopInDraftStatus() {
        Workshop draft = buildPublishedWorkshop(WORKSHOP_ID, ORG_ID, 0)
            .toBuilder()
            .status(WorkshopStatus.DRAFT)
            .build();
        when(workshopRepository.findById(WORKSHOP_ID)).thenReturn(
            Optional.of(draft)
        );

        CancelWorkshopCommand command = CancelWorkshopCommand.builder()
            .workshopId(WORKSHOP_ID)
            .organizationId(ORG_ID)
            .callerId(CALLER_ID)
            .version(0)
            .build();

        assertThatThrownBy(() -> useCase.execute(command))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining(
                "Workshop is in DRAFT status. Please use DELETE /api/v1/admin/workshops/{id} to delete drafts."
            );

        verify(workshopRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("Fail when workshop is in COMPLETED status")
    void shouldFailWhenWorkshopInCompletedStatus() {
        Workshop completed = buildPublishedWorkshop(WORKSHOP_ID, ORG_ID, 3)
            .toBuilder()
            .status(WorkshopStatus.COMPLETED)
            .build();
        when(workshopRepository.findById(WORKSHOP_ID)).thenReturn(
            Optional.of(completed)
        );

        CancelWorkshopCommand command = CancelWorkshopCommand.builder()
            .workshopId(WORKSHOP_ID)
            .organizationId(ORG_ID)
            .callerId(CALLER_ID)
            .version(3)
            .build();

        assertThatThrownBy(() -> useCase.execute(command))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining(
                "Workshops in terminal status (COMPLETED) cannot be canceled."
            );

        verify(workshopRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("Fail when workshop is in CANCELED status")
    void shouldFailWhenWorkshopInCanceledStatus() {
        Workshop canceled = buildPublishedWorkshop(WORKSHOP_ID, ORG_ID, 3)
            .toBuilder()
            .status(WorkshopStatus.CANCELED)
            .build();
        when(workshopRepository.findById(WORKSHOP_ID)).thenReturn(
            Optional.of(canceled)
        );

        CancelWorkshopCommand command = CancelWorkshopCommand.builder()
            .workshopId(WORKSHOP_ID)
            .organizationId(ORG_ID)
            .callerId(CALLER_ID)
            .version(3)
            .build();

        assertThatThrownBy(() -> useCase.execute(command))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining(
                "Workshops in terminal status (CANCELED) cannot be canceled."
            );

        verify(workshopRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("Fail when workshop has already concluded (endsAt <= now)")
    void shouldFailWhenWorkshopEndsAtInPastOrPresent() {
        Workshop concluded = buildPublishedWorkshop(WORKSHOP_ID, ORG_ID, 2)
            .toBuilder()
            .endsAt(Instant.now().minusSeconds(1800))
            .build();
        when(workshopRepository.findById(WORKSHOP_ID)).thenReturn(
            Optional.of(concluded)
        );

        CancelWorkshopCommand command = CancelWorkshopCommand.builder()
            .workshopId(WORKSHOP_ID)
            .organizationId(ORG_ID)
            .callerId(CALLER_ID)
            .version(2)
            .build();

        assertThatThrownBy(() -> useCase.execute(command))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining(
                "Workshop has already concluded and cannot be canceled."
            );

        verify(workshopRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName(
        "Propagate OptimisticLockingFailureException when repository save throws it"
    )
    void shouldPropagateOptimisticLockingFailureException() {
        Workshop existing = buildPublishedWorkshop(WORKSHOP_ID, ORG_ID, 2);
        when(workshopRepository.findById(WORKSHOP_ID)).thenReturn(
            Optional.of(existing)
        );
        when(workshopRepository.save(any(Workshop.class))).thenThrow(
            new OptimisticLockingFailureException(
                "Simulated concurrency conflict"
            )
        );

        CancelWorkshopCommand command = CancelWorkshopCommand.builder()
            .workshopId(WORKSHOP_ID)
            .organizationId(ORG_ID)
            .callerId(CALLER_ID)
            .version(2)
            .build();

        assertThatThrownBy(() -> useCase.execute(command)).isInstanceOf(
            OptimisticLockingFailureException.class
        );

        verifyNoInteractions(eventPublisher);
    }
}
