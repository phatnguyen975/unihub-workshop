package com.unihub.workshop.workshops.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.unihub.workshop.iam.api.IamApi;
import com.unihub.workshop.iam.api.dto.StaffUserDto;
import com.unihub.workshop.shared.events.StaffAssignedToWorkshopEvent;
import com.unihub.workshop.shared.exception.ConflictException;
import com.unihub.workshop.shared.exception.ForbiddenException;
import com.unihub.workshop.shared.exception.NotFoundException;
import com.unihub.workshop.shared.exception.ValidationException;
import com.unihub.workshop.workshops.application.ports.in.AssignStaffCommand;
import com.unihub.workshop.workshops.application.ports.out.WorkshopAssignmentRepository;
import com.unihub.workshop.workshops.application.ports.out.WorkshopRepository;
import com.unihub.workshop.workshops.domain.model.AssignedStaff;
import com.unihub.workshop.workshops.domain.model.Workshop;
import com.unihub.workshop.workshops.domain.model.WorkshopStatus;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;

class AssignStaffUseCaseImplTest {

    private WorkshopRepository workshopRepository;
    private WorkshopAssignmentRepository assignmentRepository;
    private IamApi iamApi;
    private ApplicationEventPublisher eventPublisher;

    private AssignStaffUseCaseImpl useCase;

    private final UUID organizationId = UUID.randomUUID();
    private final UUID callerId = UUID.randomUUID();
    private final UUID workshopId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        workshopRepository = mock(WorkshopRepository.class);
        assignmentRepository = mock(WorkshopAssignmentRepository.class);
        iamApi = mock(IamApi.class);
        eventPublisher = mock(ApplicationEventPublisher.class);

        useCase = new AssignStaffUseCaseImpl(
            workshopRepository,
            assignmentRepository,
            iamApi,
            eventPublisher
        );
    }

    private Workshop publishedWorkshop() {
        return Workshop.builder()
            .id(workshopId)
            .organizationId(organizationId)
            .title("Clean Architecture Workshop")
            .status(WorkshopStatus.PUBLISHED)
            .startsAt(Instant.now().plusSeconds(3600))
            .endsAt(Instant.now().plusSeconds(7200))
            .location("Room 101")
            .build();
    }

    @Test
    @DisplayName("Should throw NotFoundException when workshop does not exist")
    void shouldThrowNotFoundWhenWorkshopDoesNotExist() {
        when(workshopRepository.findById(workshopId)).thenReturn(
            Optional.empty()
        );

        AssignStaffCommand command = new AssignStaffCommand(
            workshopId,
            organizationId,
            callerId,
            List.of(UUID.randomUUID())
        );

        assertThatThrownBy(() -> useCase.execute(command))
            .isInstanceOf(NotFoundException.class)
            .hasMessageContaining("Workshop not found");
    }

    @Test
    @DisplayName(
        "Should throw ForbiddenException when workshop belongs to another organization"
    )
    void shouldThrowForbiddenWhenWorkshopBelongsToDifferentOrg() {
        Workshop workshop = publishedWorkshop()
            .toBuilder()
            .organizationId(UUID.randomUUID())
            .build();
        when(workshopRepository.findById(workshopId)).thenReturn(
            Optional.of(workshop)
        );

        AssignStaffCommand command = new AssignStaffCommand(
            workshopId,
            organizationId,
            callerId,
            List.of(UUID.randomUUID())
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
    void shouldThrowConflictWhenWorkshopNotPublished(
        WorkshopStatus nonPublishedStatus
    ) {
        Workshop workshop = publishedWorkshop()
            .toBuilder()
            .status(nonPublishedStatus)
            .build();
        when(workshopRepository.findById(workshopId)).thenReturn(
            Optional.of(workshop)
        );

        AssignStaffCommand command = new AssignStaffCommand(
            workshopId,
            organizationId,
            callerId,
            List.of(UUID.randomUUID())
        );

        assertThatThrownBy(() -> useCase.execute(command))
            .isInstanceOf(ConflictException.class)
            .hasMessageContaining("PUBLISHED status");
    }

    @Test
    @DisplayName(
        "Should throw ValidationException when workshop has already started"
    )
    void shouldThrowValidationExceptionWhenWorkshopAlreadyStarted() {
        Workshop startedWorkshop = publishedWorkshop()
            .toBuilder()
            .startsAt(Instant.now().minusSeconds(60))
            .endsAt(Instant.now().plusSeconds(3600))
            .build();
        when(workshopRepository.findById(workshopId)).thenReturn(
            Optional.of(startedWorkshop)
        );

        AssignStaffCommand command = new AssignStaffCommand(
            workshopId,
            organizationId,
            callerId,
            List.of(UUID.randomUUID())
        );

        assertThatThrownBy(() -> useCase.execute(command))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining(
                "Cannot assign staff after the workshop has started."
            );
    }

    @Test
    @DisplayName(
        "Should throw ValidationException when staff user does not exist or has wrong role/org/inactive"
    )
    void shouldThrowValidationExceptionWhenStaffInvalid() {
        Workshop workshop = publishedWorkshop();
        when(workshopRepository.findById(workshopId)).thenReturn(
            Optional.of(workshop)
        );

        UUID invalidStaffId = UUID.randomUUID();
        // Return empty from IAM
        when(iamApi.getStaffUsersByIds(any())).thenReturn(
            Collections.emptyMap()
        );

        AssignStaffCommand command = new AssignStaffCommand(
            workshopId,
            organizationId,
            callerId,
            List.of(invalidStaffId)
        );

        assertThatThrownBy(() -> useCase.execute(command))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining(
                "Validation failed for one or more staff members in the batch."
            );
    }

    @Test
    @DisplayName(
        "Should throw ConflictException when staff is already assigned to this workshop"
    )
    void shouldThrowConflictWhenStaffAlreadyAssigned() {
        Workshop workshop = publishedWorkshop();
        when(workshopRepository.findById(workshopId)).thenReturn(
            Optional.of(workshop)
        );

        UUID staffId = UUID.randomUUID();
        StaffUserDto staff = new StaffUserDto(
            staffId,
            organizationId,
            "STAFF",
            true,
            "Alice Nguyen",
            "alice@example.com",
            null,
            null
        );
        when(iamApi.getStaffUsersByIds(any())).thenReturn(
            Map.of(staffId, staff)
        );
        when(
            assignmentRepository.findAlreadyAssignedUserIds(
                workshopId,
                List.of(staffId)
            )
        ).thenReturn(Set.of(staffId));

        AssignStaffCommand command = new AssignStaffCommand(
            workshopId,
            organizationId,
            callerId,
            List.of(staffId)
        );

        assertThatThrownBy(() -> useCase.execute(command))
            .isInstanceOf(ConflictException.class)
            .hasMessageContaining("already assigned");
    }

    @Test
    @DisplayName(
        "Should throw ConflictException when staff has overlapping schedule with another active workshop"
    )
    void shouldThrowConflictWhenScheduleOverlaps() {
        Workshop workshop = publishedWorkshop();
        when(workshopRepository.findById(workshopId)).thenReturn(
            Optional.of(workshop)
        );

        UUID staffId = UUID.randomUUID();
        StaffUserDto staff = new StaffUserDto(
            staffId,
            organizationId,
            "STAFF",
            true,
            "Alice Nguyen",
            "alice@example.com",
            null,
            null
        );
        when(iamApi.getStaffUsersByIds(any())).thenReturn(
            Map.of(staffId, staff)
        );
        when(
            assignmentRepository.findAlreadyAssignedUserIds(
                workshopId,
                List.of(staffId)
            )
        ).thenReturn(Collections.emptySet());
        when(
            assignmentRepository.findScheduleConflictingUserIds(
                organizationId,
                workshopId,
                workshop.startsAt(),
                workshop.endsAt(),
                List.of(staffId)
            )
        ).thenReturn(Set.of(staffId));

        AssignStaffCommand command = new AssignStaffCommand(
            workshopId,
            organizationId,
            callerId,
            List.of(staffId)
        );

        assertThatThrownBy(() -> useCase.execute(command))
            .isInstanceOf(ConflictException.class)
            .hasMessageContaining("overlapping schedule");
    }

    @Test
    @DisplayName(
        "Should assign staff, save rows, and publish integration events on success"
    )
    void shouldAssignStaffSuccessfully() {
        Workshop workshop = publishedWorkshop();
        when(workshopRepository.findById(workshopId)).thenReturn(
            Optional.of(workshop)
        );

        UUID staffId1 = UUID.randomUUID();
        UUID staffId2 = UUID.randomUUID();

        StaffUserDto staff1 = new StaffUserDto(
            staffId1,
            organizationId,
            "STAFF",
            true,
            "Alice Nguyen",
            "alice@example.com",
            "https://example.com/alice.png",
            "+84901234567"
        );
        StaffUserDto staff2 = new StaffUserDto(
            staffId2,
            organizationId,
            "STAFF",
            true,
            "Bob Tran",
            "bob@example.com",
            null,
            null
        );

        when(iamApi.getStaffUsersByIds(any())).thenReturn(
            Map.of(staffId1, staff1, staffId2, staff2)
        );
        when(
            assignmentRepository.findAlreadyAssignedUserIds(any(), any())
        ).thenReturn(Collections.emptySet());
        when(
            assignmentRepository.findScheduleConflictingUserIds(
                any(),
                any(),
                any(),
                any(),
                any()
            )
        ).thenReturn(Collections.emptySet());

        when(assignmentRepository.saveAll(any())).thenAnswer(invocation ->
            invocation.getArgument(0)
        );

        AssignStaffCommand command = new AssignStaffCommand(
            workshopId,
            organizationId,
            callerId,
            List.of(staffId1, staffId2)
        );

        List<AssignedStaff> result = useCase.execute(command);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).userId()).isEqualTo(staffId1);
        assertThat(result.get(0).fullName()).isEqualTo("Alice Nguyen");
        assertThat(result.get(1).userId()).isEqualTo(staffId2);
        assertThat(result.get(1).fullName()).isEqualTo("Bob Tran");

        verify(assignmentRepository).saveAll(any());
        verify(eventPublisher, times(2)).publishEvent(
            any(StaffAssignedToWorkshopEvent.class)
        );
    }

    @Test
    @DisplayName(
        "Should catch DataIntegrityViolationException and translate to ConflictException"
    )
    void shouldTranslateDataIntegrityViolationToConflict() {
        Workshop workshop = publishedWorkshop();
        when(workshopRepository.findById(workshopId)).thenReturn(
            Optional.of(workshop)
        );

        UUID staffId = UUID.randomUUID();
        StaffUserDto staff = new StaffUserDto(
            staffId,
            organizationId,
            "STAFF",
            true,
            "Alice Nguyen",
            "alice@example.com",
            null,
            null
        );

        when(iamApi.getStaffUsersByIds(any())).thenReturn(
            Map.of(staffId, staff)
        );
        when(
            assignmentRepository.findAlreadyAssignedUserIds(any(), any())
        ).thenReturn(Collections.emptySet());
        when(
            assignmentRepository.findScheduleConflictingUserIds(
                any(),
                any(),
                any(),
                any(),
                any()
            )
        ).thenReturn(Collections.emptySet());

        when(assignmentRepository.saveAll(any())).thenThrow(
            new DataIntegrityViolationException("Unique constraint violation")
        );

        AssignStaffCommand command = new AssignStaffCommand(
            workshopId,
            organizationId,
            callerId,
            List.of(staffId)
        );

        assertThatThrownBy(() -> useCase.execute(command))
            .isInstanceOf(ConflictException.class)
            .hasMessageContaining("already assigned");
    }
}
