package com.unihub.workshop.workshops.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.unihub.workshop.shared.events.StaffRevokedFromWorkshopEvent;
import com.unihub.workshop.shared.exception.ConflictException;
import com.unihub.workshop.shared.exception.ForbiddenException;
import com.unihub.workshop.shared.exception.NotFoundException;
import com.unihub.workshop.shared.exception.ValidationException;
import com.unihub.workshop.workshops.application.ports.in.RevokeStaffCommand;
import com.unihub.workshop.workshops.application.ports.out.WorkshopAssignmentRepository;
import com.unihub.workshop.workshops.application.ports.out.WorkshopRepository;
import com.unihub.workshop.workshops.domain.model.Workshop;
import com.unihub.workshop.workshops.domain.model.WorkshopStatus;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

class RevokeStaffUseCaseImplTest {

    private WorkshopRepository workshopRepository;
    private WorkshopAssignmentRepository assignmentRepository;
    private ApplicationEventPublisher eventPublisher;

    private RevokeStaffUseCaseImpl useCase;

    private final UUID organizationId = UUID.randomUUID();
    private final UUID callerId = UUID.randomUUID();
    private final UUID workshopId = UUID.randomUUID();
    private final UUID staffUserId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        workshopRepository = mock(WorkshopRepository.class);
        assignmentRepository = mock(WorkshopAssignmentRepository.class);
        eventPublisher = mock(ApplicationEventPublisher.class);

        useCase = new RevokeStaffUseCaseImpl(
            workshopRepository,
            assignmentRepository,
            eventPublisher
        );
    }

    private Workshop publishedWorkshop() {
        return Workshop.builder()
            .id(workshopId)
            .organizationId(organizationId)
            .title("Test Workshop")
            .status(WorkshopStatus.PUBLISHED)
            .startsAt(Instant.now().plusSeconds(3600))
            .endsAt(Instant.now().plusSeconds(7200))
            .location("Hall B")
            .build();
    }

    @Test
    @DisplayName("Should throw NotFoundException when workshop does not exist")
    void shouldThrowNotFoundWhenWorkshopDoesNotExist() {
        when(workshopRepository.findById(workshopId)).thenReturn(
            Optional.empty()
        );

        RevokeStaffCommand command = new RevokeStaffCommand(
            workshopId,
            staffUserId,
            organizationId,
            callerId
        );

        assertThatThrownBy(() -> useCase.execute(command))
            .isInstanceOf(NotFoundException.class)
            .hasMessageContaining("Workshop not found");
    }

    @Test
    @DisplayName(
        "Should throw ForbiddenException when workshop belongs to different organization"
    )
    void shouldThrowForbiddenWhenDifferentOrg() {
        Workshop workshop = publishedWorkshop()
            .toBuilder()
            .organizationId(UUID.randomUUID())
            .build();
        when(workshopRepository.findById(workshopId)).thenReturn(
            Optional.of(workshop)
        );

        RevokeStaffCommand command = new RevokeStaffCommand(
            workshopId,
            staffUserId,
            organizationId,
            callerId
        );

        assertThatThrownBy(() -> useCase.execute(command))
            .isInstanceOf(ForbiddenException.class)
            .hasMessageContaining("different organization");
    }

    @ParameterizedTest
    @EnumSource(
        value = WorkshopStatus.class,
        names = { "DRAFT", "COMPLETED", "CANCELED" }
    )
    @DisplayName(
        "Should throw ConflictException when workshop is not PUBLISHED"
    )
    void shouldThrowConflictWhenNotPublished(
        WorkshopStatus nonPublishedStatus
    ) {
        Workshop workshop = publishedWorkshop()
            .toBuilder()
            .status(nonPublishedStatus)
            .build();
        when(workshopRepository.findById(workshopId)).thenReturn(
            Optional.of(workshop)
        );

        RevokeStaffCommand command = new RevokeStaffCommand(
            workshopId,
            staffUserId,
            organizationId,
            callerId
        );

        assertThatThrownBy(() -> useCase.execute(command))
            .isInstanceOf(ConflictException.class)
            .hasMessageContaining("PUBLISHED status");
    }

    @Test
    @DisplayName(
        "Should throw ValidationException when workshop has already started"
    )
    void shouldThrowValidationExceptionWhenAlreadyStarted() {
        Workshop startedWorkshop = publishedWorkshop()
            .toBuilder()
            .startsAt(Instant.now().minusSeconds(60))
            .endsAt(Instant.now().plusSeconds(3600))
            .build();
        when(workshopRepository.findById(workshopId)).thenReturn(
            Optional.of(startedWorkshop)
        );

        RevokeStaffCommand command = new RevokeStaffCommand(
            workshopId,
            staffUserId,
            organizationId,
            callerId
        );

        assertThatThrownBy(() -> useCase.execute(command))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining(
                "Cannot revoke staff after the workshop has started."
            );
    }

    @Test
    @DisplayName(
        "Should throw NotFoundException when staff member is not currently assigned"
    )
    void shouldThrowNotFoundWhenStaffNotAssigned() {
        Workshop workshop = publishedWorkshop();
        when(workshopRepository.findById(workshopId)).thenReturn(
            Optional.of(workshop)
        );
        when(
            assignmentRepository.existsByWorkshopIdAndStaffUserId(
                workshopId,
                staffUserId
            )
        ).thenReturn(false);

        RevokeStaffCommand command = new RevokeStaffCommand(
            workshopId,
            staffUserId,
            organizationId,
            callerId
        );

        assertThatThrownBy(() -> useCase.execute(command))
            .isInstanceOf(NotFoundException.class)
            .hasMessageContaining("Staff assignment not found");
    }

    @Test
    @DisplayName(
        "Should delete assignment and publish StaffRevokedFromWorkshopEvent on success"
    )
    void shouldDeleteAssignmentAndPublishEvent() {
        Workshop workshop = publishedWorkshop();
        when(workshopRepository.findById(workshopId)).thenReturn(
            Optional.of(workshop)
        );
        when(
            assignmentRepository.existsByWorkshopIdAndStaffUserId(
                workshopId,
                staffUserId
            )
        ).thenReturn(true);

        RevokeStaffCommand command = new RevokeStaffCommand(
            workshopId,
            staffUserId,
            organizationId,
            callerId
        );

        useCase.execute(command);

        verify(assignmentRepository).deleteByWorkshopIdAndStaffUserId(
            workshopId,
            staffUserId
        );

        ArgumentCaptor<StaffRevokedFromWorkshopEvent> captor =
            ArgumentCaptor.forClass(StaffRevokedFromWorkshopEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());

        StaffRevokedFromWorkshopEvent event = captor.getValue();
        assertThat(event.workshopId()).isEqualTo(workshopId);
        assertThat(event.staffUserId()).isEqualTo(staffUserId);
        assertThat(event.organizationId()).isEqualTo(organizationId);
        assertThat(event.revokedBy()).isEqualTo(callerId);
        assertThat(event.workshopTitle()).isEqualTo("Test Workshop");
    }
}
