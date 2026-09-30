package com.unihub.workshop.workshops.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.unihub.workshop.iam.api.IamApi;
import com.unihub.workshop.iam.api.dto.StaffUserDto;
import com.unihub.workshop.shared.exception.ConflictException;
import com.unihub.workshop.shared.exception.ForbiddenException;
import com.unihub.workshop.shared.exception.NotFoundException;
import com.unihub.workshop.shared.exception.ValidationException;
import com.unihub.workshop.shared.pagination.CursorPage;
import com.unihub.workshop.shared.pagination.KeysetCursor;
import com.unihub.workshop.shared.pagination.KeysetCursorCodec;
import com.unihub.workshop.workshops.application.ports.in.ListAvailableStaffQuery;
import com.unihub.workshop.workshops.application.ports.out.WorkshopAssignmentRepository;
import com.unihub.workshop.workshops.application.ports.out.WorkshopRepository;
import com.unihub.workshop.workshops.domain.model.AvailableStaff;
import com.unihub.workshop.workshops.domain.model.Workshop;
import com.unihub.workshop.workshops.domain.model.WorkshopStatus;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ListAvailableStaffUseCaseImplTest {

    @Mock
    private WorkshopRepository workshopRepository;

    @Mock
    private WorkshopAssignmentRepository assignmentRepository;

    @Mock
    private IamApi iamApi;

    private ListAvailableStaffUseCaseImpl useCase;

    private final UUID organizationId = UUID.randomUUID();
    private final UUID workshopId = UUID.randomUUID();
    private final Instant futureStartsAt = Instant.now().plusSeconds(86400);
    private final Instant futureEndsAt = futureStartsAt.plusSeconds(7200);

    @BeforeEach
    void setUp() {
        useCase = new ListAvailableStaffUseCaseImpl(
            workshopRepository,
            assignmentRepository,
            iamApi
        );
    }

    private Workshop publishedWorkshop() {
        return Workshop.builder()
            .id(workshopId)
            .organizationId(organizationId)
            .status(WorkshopStatus.PUBLISHED)
            .startsAt(futureStartsAt)
            .endsAt(futureEndsAt)
            .build();
    }

    @Test
    @DisplayName("Should throw NotFoundException when workshop does not exist")
    void shouldThrowNotFoundWhenWorkshopDoesNotExist() {
        when(workshopRepository.findById(workshopId)).thenReturn(
            Optional.empty()
        );

        ListAvailableStaffQuery query = new ListAvailableStaffQuery(
            workshopId,
            organizationId,
            null,
            null,
            20
        );

        assertThatThrownBy(() -> useCase.execute(query))
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

        ListAvailableStaffQuery query = new ListAvailableStaffQuery(
            workshopId,
            organizationId,
            null,
            null,
            20
        );

        assertThatThrownBy(() -> useCase.execute(query))
            .isInstanceOf(ForbiddenException.class)
            .hasMessageContaining("different organization");
    }

    @Test
    @DisplayName(
        "Should throw ConflictException when workshop is in DRAFT status"
    )
    void shouldThrowConflictWhenWorkshopInDraft() {
        Workshop workshop = publishedWorkshop()
            .toBuilder()
            .status(WorkshopStatus.DRAFT)
            .build();
        when(workshopRepository.findById(workshopId)).thenReturn(
            Optional.of(workshop)
        );

        ListAvailableStaffQuery query = new ListAvailableStaffQuery(
            workshopId,
            organizationId,
            null,
            null,
            20
        );

        assertThatThrownBy(() -> useCase.execute(query))
            .isInstanceOf(ConflictException.class)
            .hasMessageContaining("PUBLISHED");
    }

    @Test
    @DisplayName(
        "Should throw ConflictException when workshop is COMPLETED or CANCELED"
    )
    void shouldThrowConflictWhenWorkshopCompletedOrCanceled() {
        Workshop completed = publishedWorkshop()
            .toBuilder()
            .status(WorkshopStatus.COMPLETED)
            .build();
        when(workshopRepository.findById(workshopId)).thenReturn(
            Optional.of(completed)
        );

        ListAvailableStaffQuery query = new ListAvailableStaffQuery(
            workshopId,
            organizationId,
            null,
            null,
            20
        );

        assertThatThrownBy(() -> useCase.execute(query)).isInstanceOf(
            ConflictException.class
        );
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

        ListAvailableStaffQuery query = new ListAvailableStaffQuery(
            workshopId,
            organizationId,
            null,
            null,
            20
        );

        assertThatThrownBy(() -> useCase.execute(query))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("already started");
    }

    @Test
    @DisplayName(
        "Should exclude staff already assigned to target workshop or with schedule conflict"
    )
    void shouldFilterAssignedAndConflictingStaff() {
        Workshop workshop = publishedWorkshop();
        when(workshopRepository.findById(workshopId)).thenReturn(
            Optional.of(workshop)
        );

        UUID staffAvailable = UUID.randomUUID();
        UUID staffAlreadyAssigned = UUID.randomUUID();
        UUID staffConflicting = UUID.randomUUID();

        StaffUserDto staff1 = new StaffUserDto(
            staffAvailable,
            organizationId,
            "STAFF",
            true,
            "Alice Nguyen",
            "alice@example.com",
            null,
            null
        );
        StaffUserDto staff2 = new StaffUserDto(
            staffAlreadyAssigned,
            organizationId,
            "STAFF",
            true,
            "Bob Tran",
            "bob@example.com",
            null,
            null
        );
        StaffUserDto staff3 = new StaffUserDto(
            staffConflicting,
            organizationId,
            "STAFF",
            true,
            "Charlie Le",
            "charlie@example.com",
            null,
            null
        );

        when(iamApi.listActiveStaffByOrganization(organizationId)).thenReturn(
            List.of(staff1, staff2, staff3)
        );
        when(
            assignmentRepository.findAssignedStaffUserIds(workshopId)
        ).thenReturn(Set.of(staffAlreadyAssigned));
        when(
            assignmentRepository.findScheduleConflictingStaffUserIds(
                organizationId,
                workshopId,
                futureStartsAt,
                futureEndsAt
            )
        ).thenReturn(Set.of(staffConflicting));
        when(
            assignmentRepository.countActiveAssignmentsForUsers(
                organizationId,
                List.of(staffAvailable)
            )
        ).thenReturn(Map.of(staffAvailable, 3));

        ListAvailableStaffQuery query = new ListAvailableStaffQuery(
            workshopId,
            organizationId,
            null,
            null,
            20
        );
        CursorPage<AvailableStaff> result = useCase.execute(query);

        assertThat(result.items()).hasSize(1);
        AvailableStaff item = result.items().get(0);
        assertThat(item.userId()).isEqualTo(staffAvailable);
        assertThat(item.fullName()).isEqualTo("Alice Nguyen");
        assertThat(item.activeAssignmentsCount()).isEqualTo(3);
    }

    @Test
    @DisplayName(
        "Should filter available staff by keyword q matching fullName or email case-insensitively"
    )
    void shouldFilterByKeywordQ() {
        Workshop workshop = publishedWorkshop();
        when(workshopRepository.findById(workshopId)).thenReturn(
            Optional.of(workshop)
        );

        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();

        StaffUserDto staff1 = new StaffUserDto(
            id1,
            organizationId,
            "STAFF",
            true,
            "Alice Nguyen",
            "alice@example.com",
            null,
            null
        );
        StaffUserDto staff2 = new StaffUserDto(
            id2,
            organizationId,
            "STAFF",
            true,
            "David Beckham",
            "david@example.com",
            null,
            null
        );

        when(iamApi.listActiveStaffByOrganization(organizationId)).thenReturn(
            List.of(staff1, staff2)
        );
        when(
            assignmentRepository.findAssignedStaffUserIds(workshopId)
        ).thenReturn(Set.of());
        when(
            assignmentRepository.findScheduleConflictingStaffUserIds(
                organizationId,
                workshopId,
                futureStartsAt,
                futureEndsAt
            )
        ).thenReturn(Set.of());
        when(
            assignmentRepository.countActiveAssignmentsForUsers(
                organizationId,
                List.of(id1)
            )
        ).thenReturn(Map.of(id1, 1));

        ListAvailableStaffQuery query = new ListAvailableStaffQuery(
            workshopId,
            organizationId,
            "LiCe",
            null,
            20
        );
        CursorPage<AvailableStaff> result = useCase.execute(query);

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).fullName()).isEqualTo("Alice Nguyen");
    }

    @Test
    @DisplayName(
        "Should not throw NullPointerException when staff fullName is null during cursor pagination"
    )
    void shouldHandleNullFullNameWithoutThrowingNpeWhenPaginatingWithCursor() {
        Workshop workshop = publishedWorkshop();
        when(workshopRepository.findById(workshopId)).thenReturn(
            Optional.of(workshop)
        );

        UUID id1 = UUID.randomUUID();
        UUID idNullName = UUID.randomUUID();

        StaffUserDto staff1 = new StaffUserDto(
            id1,
            organizationId,
            "STAFF",
            true,
            "Alice Nguyen",
            "alice@example.com",
            null,
            null
        );
        StaffUserDto staffNullName = new StaffUserDto(
            idNullName,
            organizationId,
            "STAFF",
            true,
            null,
            "noname@example.com",
            null,
            null
        );

        when(iamApi.listActiveStaffByOrganization(organizationId)).thenReturn(
            List.of(staff1, staffNullName)
        );
        when(
            assignmentRepository.findAssignedStaffUserIds(workshopId)
        ).thenReturn(Set.of());
        when(
            assignmentRepository.findScheduleConflictingStaffUserIds(
                organizationId,
                workshopId,
                futureStartsAt,
                futureEndsAt
            )
        ).thenReturn(Set.of());
        when(
            assignmentRepository.countActiveAssignmentsForUsers(
                eq(organizationId),
                any()
            )
        ).thenReturn(Map.of(idNullName, 0));

        String cursor = KeysetCursorCodec.encode(
            KeysetCursor.of(id1, "fullName", "Alice Nguyen")
        );

        ListAvailableStaffQuery query = new ListAvailableStaffQuery(
            workshopId,
            organizationId,
            null,
            cursor,
            20
        );

        CursorPage<AvailableStaff> result = useCase.execute(query);

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).userId()).isEqualTo(idNullName);
    }
}
