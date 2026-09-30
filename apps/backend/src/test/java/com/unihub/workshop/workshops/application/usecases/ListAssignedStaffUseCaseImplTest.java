package com.unihub.workshop.workshops.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.unihub.workshop.iam.api.IamApi;
import com.unihub.workshop.iam.api.dto.StaffUserDto;
import com.unihub.workshop.shared.exception.ForbiddenException;
import com.unihub.workshop.shared.exception.NotFoundException;
import com.unihub.workshop.shared.pagination.CursorPage;
import com.unihub.workshop.workshops.application.ports.in.ListAssignedStaffQuery;
import com.unihub.workshop.workshops.application.ports.out.WorkshopAssignmentRepository;
import com.unihub.workshop.workshops.application.ports.out.WorkshopRepository;
import com.unihub.workshop.workshops.domain.model.AssignedStaff;
import com.unihub.workshop.workshops.domain.model.Workshop;
import com.unihub.workshop.workshops.domain.model.WorkshopAssignment;
import com.unihub.workshop.workshops.domain.model.WorkshopStatus;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ListAssignedStaffUseCaseImplTest {

    @Mock
    private WorkshopRepository workshopRepository;

    @Mock
    private WorkshopAssignmentRepository assignmentRepository;

    @Mock
    private IamApi iamApi;

    private ListAssignedStaffUseCaseImpl useCase;

    private final UUID organizationId = UUID.randomUUID();
    private final UUID workshopId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        useCase = new ListAssignedStaffUseCaseImpl(
            workshopRepository,
            assignmentRepository,
            iamApi
        );
    }

    @Test
    @DisplayName("Should throw NotFoundException when workshop does not exist")
    void shouldThrowNotFoundWhenWorkshopDoesNotExist() {
        when(workshopRepository.findById(workshopId)).thenReturn(
            Optional.empty()
        );

        ListAssignedStaffQuery query = new ListAssignedStaffQuery(
            workshopId,
            organizationId,
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
        UUID otherOrgId = UUID.randomUUID();
        Workshop workshop = Workshop.builder()
            .id(workshopId)
            .organizationId(otherOrgId)
            .status(WorkshopStatus.PUBLISHED)
            .build();
        when(workshopRepository.findById(workshopId)).thenReturn(
            Optional.of(workshop)
        );

        ListAssignedStaffQuery query = new ListAssignedStaffQuery(
            workshopId,
            organizationId,
            null,
            20
        );

        assertThatThrownBy(() -> useCase.execute(query))
            .isInstanceOf(ForbiddenException.class)
            .hasMessageContaining("different organization");
    }

    @Test
    @DisplayName(
        "Should return empty cursor page when workshop has no assigned staff"
    )
    void shouldReturnEmptyPageWhenNoStaffAssigned() {
        Workshop workshop = Workshop.builder()
            .id(workshopId)
            .organizationId(organizationId)
            .status(WorkshopStatus.DRAFT)
            .build();
        when(workshopRepository.findById(workshopId)).thenReturn(
            Optional.of(workshop)
        );
        when(
            assignmentRepository.findByWorkshopId(workshopId, null, 20)
        ).thenReturn(new CursorPage<>(List.of(), null, false));

        ListAssignedStaffQuery query = new ListAssignedStaffQuery(
            workshopId,
            organizationId,
            null,
            20
        );
        CursorPage<AssignedStaff> result = useCase.execute(query);

        assertThat(result.items()).isEmpty();
        assertThat(result.hasMore()).isFalse();
        assertThat(result.nextCursor()).isNull();
    }

    @Test
    @DisplayName(
        "Should return assigned staff enriched with IAM profile details"
    )
    void shouldReturnAssignedStaffWithProfiles() {
        Workshop workshop = Workshop.builder()
            .id(workshopId)
            .organizationId(organizationId)
            .status(WorkshopStatus.PUBLISHED)
            .build();
        when(workshopRepository.findById(workshopId)).thenReturn(
            Optional.of(workshop)
        );

        UUID staffUserId1 = UUID.randomUUID();
        UUID staffUserId2 = UUID.randomUUID();
        Instant assignedAt1 = Instant.parse("2026-09-29T10:00:00Z");
        Instant assignedAt2 = Instant.parse("2026-09-29T09:00:00Z");

        WorkshopAssignment wa1 = WorkshopAssignment.builder()
            .id(UUID.randomUUID())
            .workshopId(workshopId)
            .staffUserId(staffUserId1)
            .assignedAt(assignedAt1)
            .build();
        WorkshopAssignment wa2 = WorkshopAssignment.builder()
            .id(UUID.randomUUID())
            .workshopId(workshopId)
            .staffUserId(staffUserId2)
            .assignedAt(assignedAt2)
            .build();

        when(
            assignmentRepository.findByWorkshopId(workshopId, null, 20)
        ).thenReturn(
            new CursorPage<>(List.of(wa1, wa2), "nextCursor123", true)
        );

        StaffUserDto staff1 = new StaffUserDto(
            staffUserId1,
            organizationId,
            "STAFF",
            true,
            "Alice Nguyen",
            "alice@example.com",
            "https://avatar/alice.jpg",
            "+84901234567"
        );
        StaffUserDto staff2 = new StaffUserDto(
            staffUserId2,
            organizationId,
            "STAFF",
            true,
            "Bob Tran",
            "bob@example.com",
            null,
            null
        );

        when(
            iamApi.getStaffUsersByIds(List.of(staffUserId1, staffUserId2))
        ).thenReturn(Map.of(staffUserId1, staff1, staffUserId2, staff2));

        ListAssignedStaffQuery query = new ListAssignedStaffQuery(
            workshopId,
            organizationId,
            null,
            20
        );
        CursorPage<AssignedStaff> result = useCase.execute(query);

        assertThat(result.items()).hasSize(2);
        assertThat(result.hasMore()).isTrue();
        assertThat(result.nextCursor()).isEqualTo("nextCursor123");

        AssignedStaff first = result.items().get(0);
        assertThat(first.userId()).isEqualTo(staffUserId1);
        assertThat(first.fullName()).isEqualTo("Alice Nguyen");
        assertThat(first.email()).isEqualTo("alice@example.com");
        assertThat(first.avatarUrl()).isEqualTo("https://avatar/alice.jpg");
        assertThat(first.phoneNumber()).isEqualTo("+84901234567");
        assertThat(first.assignedAt()).isEqualTo(assignedAt1);

        AssignedStaff second = result.items().get(1);
        assertThat(second.userId()).isEqualTo(staffUserId2);
        assertThat(second.fullName()).isEqualTo("Bob Tran");
        assertThat(second.email()).isEqualTo("bob@example.com");
        assertThat(second.avatarUrl()).isNull();
        assertThat(second.phoneNumber()).isNull();
        assertThat(second.assignedAt()).isEqualTo(assignedAt2);

        verify(iamApi).getStaffUsersByIds(List.of(staffUserId1, staffUserId2));
    }
}
