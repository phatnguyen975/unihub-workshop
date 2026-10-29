package com.unihub.workshop.workshops.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.unihub.workshop.shared.exception.BadRequestException;
import com.unihub.workshop.shared.pagination.CursorPage;
import com.unihub.workshop.workshops.application.ports.in.ListStaffAssignedWorkshopsQuery;
import com.unihub.workshop.workshops.application.ports.out.WorkshopAssignmentRepository;
import com.unihub.workshop.workshops.domain.model.StaffAssignedWorkshop;
import com.unihub.workshop.workshops.domain.model.WorkshopStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ListStaffAssignedWorkshopsUseCaseImplTest {

    @Mock
    private WorkshopAssignmentRepository assignmentRepository;

    private ListStaffAssignedWorkshopsUseCaseImpl useCase;

    private final UUID staffUserId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        useCase = new ListStaffAssignedWorkshopsUseCaseImpl(
            assignmentRepository
        );
    }

    @Test
    @DisplayName(
        "Should return paginated assigned workshops when valid query provided"
    )
    void shouldReturnPaginatedAssignedWorkshops_whenValidQueryProvided() {
        StaffAssignedWorkshop item = new StaffAssignedWorkshop(
            UUID.randomUUID(),
            "Python Bootcamp",
            "python-bootcamp",
            "Description",
            "https://img.png",
            "Room 101",
            Instant.parse("2026-11-15T09:00:00Z"),
            Instant.parse("2026-11-15T12:00:00Z"),
            WorkshopStatus.PUBLISHED,
            "Faculty of CS",
            Instant.parse("2026-11-01T10:00:00Z")
        );
        CursorPage<StaffAssignedWorkshop> expectedPage = new CursorPage<>(
            List.of(item),
            "next-cursor",
            true
        );
        when(
            assignmentRepository.findAssignedWorkshopsByStaffUserId(
                staffUserId,
                null,
                20
            )
        ).thenReturn(expectedPage);

        ListStaffAssignedWorkshopsQuery query =
            new ListStaffAssignedWorkshopsQuery(staffUserId, null, 20);
        CursorPage<StaffAssignedWorkshop> result = useCase.execute(query);

        assertThat(result).isNotNull();
        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).title()).isEqualTo("Python Bootcamp");
        assertThat(result.nextCursor()).isEqualTo("next-cursor");
        assertThat(result.hasMore()).isTrue();
        verify(assignmentRepository).findAssignedWorkshopsByStaffUserId(
            staffUserId,
            null,
            20
        );
    }

    @Test
    @DisplayName("Should default page size to 20 when size is null")
    void shouldDefaultPageSizeTo20_whenSizeIsNull() {
        CursorPage<StaffAssignedWorkshop> expectedPage = new CursorPage<>(
            List.of(),
            null,
            false
        );
        when(
            assignmentRepository.findAssignedWorkshopsByStaffUserId(
                staffUserId,
                null,
                20
            )
        ).thenReturn(expectedPage);

        ListStaffAssignedWorkshopsQuery query =
            new ListStaffAssignedWorkshopsQuery(staffUserId, null, null);
        CursorPage<StaffAssignedWorkshop> result = useCase.execute(query);

        assertThat(result).isNotNull();
        assertThat(result.items()).isEmpty();
        verify(assignmentRepository).findAssignedWorkshopsByStaffUserId(
            staffUserId,
            null,
            20
        );
    }

    @Test
    @DisplayName("Should throw BadRequestException when size is less than 1")
    void shouldThrowBadRequestException_whenSizeIsLessThan1() {
        ListStaffAssignedWorkshopsQuery query =
            new ListStaffAssignedWorkshopsQuery(staffUserId, null, 0);

        assertThatThrownBy(() -> useCase.execute(query))
            .isInstanceOf(BadRequestException.class)
            .hasMessageContaining(
                "The 'size' parameter must be between 1 and 100."
            );
    }

    @Test
    @DisplayName(
        "Should throw BadRequestException when size is greater than 100"
    )
    void shouldThrowBadRequestException_whenSizeIsGreaterThan100() {
        ListStaffAssignedWorkshopsQuery query =
            new ListStaffAssignedWorkshopsQuery(staffUserId, null, 101);

        assertThatThrownBy(() -> useCase.execute(query))
            .isInstanceOf(BadRequestException.class)
            .hasMessageContaining(
                "The 'size' parameter must be between 1 and 100."
            );
    }

    @Test
    @DisplayName("Should propagate cursor and custom size to repository")
    void shouldPropagateCursorAndCustomSize_whenProvided() {
        CursorPage<StaffAssignedWorkshop> expectedPage = new CursorPage<>(
            List.of(),
            null,
            false
        );
        when(
            assignmentRepository.findAssignedWorkshopsByStaffUserId(
                staffUserId,
                "prev-cursor",
                50
            )
        ).thenReturn(expectedPage);

        ListStaffAssignedWorkshopsQuery query =
            new ListStaffAssignedWorkshopsQuery(staffUserId, "prev-cursor", 50);
        CursorPage<StaffAssignedWorkshop> result = useCase.execute(query);

        assertThat(result).isNotNull();
        verify(assignmentRepository).findAssignedWorkshopsByStaffUserId(
            staffUserId,
            "prev-cursor",
            50
        );
    }
}
