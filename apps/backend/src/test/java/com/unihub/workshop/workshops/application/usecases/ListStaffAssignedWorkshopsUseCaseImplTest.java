package com.unihub.workshop.workshops.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.unihub.workshop.shared.exception.BadRequestException;
import com.unihub.workshop.shared.pagination.CursorPage;
import com.unihub.workshop.workshops.application.ports.in.ListStaffAssignedWorkshopsQuery;
import com.unihub.workshop.workshops.application.ports.out.WorkshopAssignmentRepository;
import com.unihub.workshop.workshops.domain.model.StaffAssignedWorkshop;
import com.unihub.workshop.workshops.domain.model.StaffWorkshopFilter;
import com.unihub.workshop.workshops.domain.model.WorkshopStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
                eq(staffUserId),
                eq(null),
                eq(20),
                any(StaffWorkshopFilter.class)
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
            eq(staffUserId),
            eq(null),
            eq(20),
            any(StaffWorkshopFilter.class)
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
                eq(staffUserId),
                eq(null),
                eq(20),
                any(StaffWorkshopFilter.class)
            )
        ).thenReturn(expectedPage);

        ListStaffAssignedWorkshopsQuery query =
            new ListStaffAssignedWorkshopsQuery(staffUserId, null, null);
        CursorPage<StaffAssignedWorkshop> result = useCase.execute(query);

        assertThat(result).isNotNull();
        assertThat(result.items()).isEmpty();
        verify(assignmentRepository).findAssignedWorkshopsByStaffUserId(
            eq(staffUserId),
            eq(null),
            eq(20),
            any(StaffWorkshopFilter.class)
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
        verify(
            assignmentRepository,
            never()
        ).findAssignedWorkshopsByStaffUserId(
            any(),
            any(),
            any(int.class),
            any()
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
        verify(
            assignmentRepository,
            never()
        ).findAssignedWorkshopsByStaffUserId(
            any(),
            any(),
            any(int.class),
            any()
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
                eq(staffUserId),
                eq("prev-cursor"),
                eq(50),
                any(StaffWorkshopFilter.class)
            )
        ).thenReturn(expectedPage);

        ListStaffAssignedWorkshopsQuery query =
            new ListStaffAssignedWorkshopsQuery(staffUserId, "prev-cursor", 50);
        CursorPage<StaffAssignedWorkshop> result = useCase.execute(query);

        assertThat(result).isNotNull();
        verify(assignmentRepository).findAssignedWorkshopsByStaffUserId(
            eq(staffUserId),
            eq("prev-cursor"),
            eq(50),
            any(StaffWorkshopFilter.class)
        );
    }

    @Test
    @DisplayName("Should parse and propagate active filters to repository")
    void shouldParseAndPropagateActiveFilters_whenProvided() {
        CursorPage<StaffAssignedWorkshop> expectedPage = new CursorPage<>(
            List.of(),
            null,
            false
        );
        when(
            assignmentRepository.findAssignedWorkshopsByStaffUserId(
                eq(staffUserId),
                eq("cursor-1"),
                eq(20),
                any(StaffWorkshopFilter.class)
            )
        ).thenReturn(expectedPage);

        ListStaffAssignedWorkshopsQuery query =
            new ListStaffAssignedWorkshopsQuery(
                staffUserId,
                "cursor-1",
                20,
                "PUBLISHED",
                "2026-11-12T00:00:00Z",
                "2026-11-18T00:00:00Z",
                "python"
            );

        CursorPage<StaffAssignedWorkshop> result = useCase.execute(query);

        assertThat(result).isNotNull();
        ArgumentCaptor<StaffWorkshopFilter> filterCaptor =
            ArgumentCaptor.forClass(StaffWorkshopFilter.class);
        verify(assignmentRepository).findAssignedWorkshopsByStaffUserId(
            eq(staffUserId),
            eq("cursor-1"),
            eq(20),
            filterCaptor.capture()
        );

        StaffWorkshopFilter captured = filterCaptor.getValue();
        assertThat(captured.status()).isEqualTo(WorkshopStatus.PUBLISHED);
        assertThat(captured.from()).isEqualTo(
            Instant.parse("2026-11-12T00:00:00Z")
        );
        assertThat(captured.to()).isEqualTo(
            Instant.parse("2026-11-18T00:00:00Z")
        );
        assertThat(captured.q()).isEqualTo("python");
    }

    @Test
    @DisplayName(
        "Should throw BadRequestException when invalid status provided"
    )
    void shouldThrowBadRequestException_whenInvalidStatusProvided() {
        ListStaffAssignedWorkshopsQuery query =
            new ListStaffAssignedWorkshopsQuery(
                staffUserId,
                null,
                20,
                "CANCELED",
                null,
                null,
                null
            );

        assertThatThrownBy(() -> useCase.execute(query))
            .isInstanceOf(BadRequestException.class)
            .hasMessageContaining(
                "The 'status' parameter must be 'PUBLISHED' or 'COMPLETED'."
            );
        verify(
            assignmentRepository,
            never()
        ).findAssignedWorkshopsByStaffUserId(
            any(),
            any(),
            any(int.class),
            any()
        );
    }

    @Test
    @DisplayName("Should throw BadRequestException when from is after to")
    void shouldThrowBadRequestException_whenFromIsAfterTo() {
        ListStaffAssignedWorkshopsQuery query =
            new ListStaffAssignedWorkshopsQuery(
                staffUserId,
                null,
                20,
                null,
                "2026-11-20T00:00:00Z",
                "2026-11-10T00:00:00Z",
                null
            );

        assertThatThrownBy(() -> useCase.execute(query))
            .isInstanceOf(BadRequestException.class)
            .hasMessageContaining(
                "The 'from' timestamp must be before or equal to the 'to' timestamp."
            );
        verify(
            assignmentRepository,
            never()
        ).findAssignedWorkshopsByStaffUserId(
            any(),
            any(),
            any(int.class),
            any()
        );
    }

    @Test
    @DisplayName(
        "Should throw BadRequestException when q exceeds 200 characters"
    )
    void shouldThrowBadRequestException_whenQExceeds200Characters() {
        ListStaffAssignedWorkshopsQuery query =
            new ListStaffAssignedWorkshopsQuery(
                staffUserId,
                null,
                20,
                null,
                null,
                null,
                "a".repeat(201)
            );

        assertThatThrownBy(() -> useCase.execute(query))
            .isInstanceOf(BadRequestException.class)
            .hasMessageContaining(
                "The 'q' parameter must not exceed 200 characters."
            );
        verify(
            assignmentRepository,
            never()
        ).findAssignedWorkshopsByStaffUserId(
            any(),
            any(),
            any(int.class),
            any()
        );
    }

    @Test
    @DisplayName(
        "Should forward the exact size to the repository without defaulting in the adapter layer"
    )
    void shouldForwardExactSizeToRepository_sf03Regression() {
        // Use size=1 (minimum valid) — if adapter applied "size > 0 ? size : 20"
        // this would still pass, but size=1 proves the contract boundary is honored.
        // More critically, this verifies the use case layer — not the adapter —
        // is the sole owner of size semantics.
        CursorPage<StaffAssignedWorkshop> expectedPage = new CursorPage<>(
            List.of(),
            null,
            false
        );
        when(
            assignmentRepository.findAssignedWorkshopsByStaffUserId(
                eq(staffUserId),
                eq(null),
                eq(1),
                any(StaffWorkshopFilter.class)
            )
        ).thenReturn(expectedPage);

        ListStaffAssignedWorkshopsQuery query =
            new ListStaffAssignedWorkshopsQuery(staffUserId, null, 1);
        CursorPage<StaffAssignedWorkshop> result = useCase.execute(query);

        assertThat(result).isNotNull();
        // The strict eq(1) mock expectation verifies size=1 was forwarded exactly.
        verify(assignmentRepository).findAssignedWorkshopsByStaffUserId(
            eq(staffUserId),
            eq(null),
            eq(1),
            any(StaffWorkshopFilter.class)
        );
    }

    @Test
    @DisplayName(
        "[SF-03] Should forward the exact size=100 (maximum) to repository without capping"
    )
    void shouldForwardMaxSizeToRepository_sf03Regression() {
        CursorPage<StaffAssignedWorkshop> expectedPage = new CursorPage<>(
            List.of(),
            null,
            false
        );
        when(
            assignmentRepository.findAssignedWorkshopsByStaffUserId(
                eq(staffUserId),
                eq(null),
                eq(100),
                any(StaffWorkshopFilter.class)
            )
        ).thenReturn(expectedPage);

        ListStaffAssignedWorkshopsQuery query =
            new ListStaffAssignedWorkshopsQuery(staffUserId, null, 100);
        CursorPage<StaffAssignedWorkshop> result = useCase.execute(query);

        assertThat(result).isNotNull();
        verify(assignmentRepository).findAssignedWorkshopsByStaffUserId(
            eq(staffUserId),
            eq(null),
            eq(100),
            any(StaffWorkshopFilter.class)
        );
    }
}
