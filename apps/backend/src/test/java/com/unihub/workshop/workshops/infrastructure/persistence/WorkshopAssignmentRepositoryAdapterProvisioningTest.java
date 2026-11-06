package com.unihub.workshop.workshops.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.unihub.workshop.shared.exception.BadRequestException;
import com.unihub.workshop.shared.pagination.CursorPage;
import com.unihub.workshop.shared.pagination.KeysetCursor;
import com.unihub.workshop.shared.pagination.KeysetCursorCodec;
import com.unihub.workshop.workshops.domain.model.StaffAssignedWorkshop;
import com.unihub.workshop.workshops.domain.model.StaffWorkshopFilter;
import com.unihub.workshop.workshops.domain.model.WorkshopStatus;
import com.unihub.workshop.workshops.infrastructure.persistence.mapper.WorkshopAssignmentPersistenceMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class WorkshopAssignmentRepositoryAdapterProvisioningTest {

    private final EntityManager entityManager = mock(EntityManager.class);
    private final Query query = mock(Query.class);
    private final UUID staffId = UUID.randomUUID();
    private final UUID workshopId = UUID.randomUUID();
    private final Instant startsAt = Instant.parse("2026-11-15T09:00:00Z");
    private final Instant assignedAt = Instant.parse("2026-11-01T10:00:00Z");
    private WorkshopAssignmentRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new WorkshopAssignmentRepositoryAdapter(
            mock(WorkshopAssignmentJpaRepository.class),
            mock(WorkshopAssignmentPersistenceMapper.class),
            entityManager
        );
        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
    }

    @Test
    void mapsRowsAndUsesBoundedParameterizedAscendingQuery() {
        StaffWorkshopFilter filter = new StaffWorkshopFilter(
            WorkshopStatus.PUBLISHED,
            startsAt,
            null,
            "python"
        );
        Object[] row = row(workshopId, startsAt);
        Object[] extraRow = row(UUID.randomUUID(), startsAt.plusSeconds(60));
        when(query.getResultList()).thenReturn(Arrays.asList(row, extraRow));

        CursorPage<StaffAssignedWorkshop> page =
            adapter.findAssignedWorkshopsByStaffUserId(
                staffId,
                null,
                1,
                filter
            );

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(entityManager).createNativeQuery(sql.capture());
        assertThat(sql.getValue())
            .contains(
                "a.staff_user_id = :staffUserId",
                "w.status <> 'CANCELED'"
            )
            .contains(
                "w.status = :status",
                "w.starts_at >= :fromTimestamp",
                "LIKE :qPattern"
            )
            .contains(
                "ORDER BY w.starts_at ASC NULLS LAST, w.id ASC LIMIT :queryLimit"
            )
            .doesNotContain(staffId.toString(), "python");
        verify(query).setParameter("staffUserId", staffId);
        verify(query).setParameter("status", "PUBLISHED");
        verify(query).setParameter("fromTimestamp", Timestamp.from(startsAt));
        verify(query).setParameter("qPattern", "%python%");
        verify(query).setParameter("queryLimit", 2);
        assertThat(page.hasMore()).isTrue();
        assertThat(page.items()).hasSize(1);
        StaffAssignedWorkshop item = page.items().getFirst();
        assertThat(item.id()).isEqualTo(workshopId);
        assertThat(item.title()).isEqualTo("Python");
        assertThat(item.startsAt()).isEqualTo(startsAt);
        assertThat(item.status()).isEqualTo(WorkshopStatus.PUBLISHED);
        assertThat(item.assignedAt()).isEqualTo(assignedAt);
        KeysetCursor cursor = KeysetCursorCodec.decode(
            page.nextCursor(),
            "startsAt",
            filter.toFingerprint()
        );
        assertThat(cursor.id()).isEqualTo(workshopId);
        assertThat(cursor.asInstant()).isEqualTo(startsAt);
    }

    @Test
    void seekUsesAscendingStartsAtAndIdAndAllowsNullStartsAt() {
        StaffWorkshopFilter filter = StaffWorkshopFilter.empty();
        String cursor = KeysetCursorCodec.encode(
            new KeysetCursor(
                workshopId,
                "startsAt",
                startsAt.toString(),
                null,
                filter.toFingerprint()
            )
        );
        when(query.getResultList()).thenReturn(List.of());

        adapter.findAssignedWorkshopsByStaffUserId(staffId, cursor, 20, filter);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(entityManager).createNativeQuery(sql.capture());
        assertThat(sql.getValue()).contains(
            "w.starts_at > :cursorStartsAt",
            "w.id > :cursorId",
            "w.starts_at IS NULL"
        );
        verify(query).setParameter("cursorStartsAt", Timestamp.from(startsAt));
        verify(query).setParameter("cursorId", workshopId);
        verify(query).setParameter("queryLimit", 21);
    }

    @Test
    void nullStartCursorSeeksOnlyNullRowsByAscendingId() {
        StaffWorkshopFilter filter = StaffWorkshopFilter.empty();
        String cursor = KeysetCursorCodec.encode(
            new KeysetCursor(
                workshopId,
                "startsAt",
                null,
                null,
                filter.toFingerprint()
            )
        );
        when(query.getResultList()).thenReturn(List.of());

        adapter.findAssignedWorkshopsByStaffUserId(
            staffId,
            cursor,
            100,
            filter
        );

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(entityManager).createNativeQuery(sql.capture());
        assertThat(sql.getValue())
            .contains("AND w.starts_at IS NULL", "AND w.id > :cursorId")
            .doesNotContain(":cursorStartsAt");
        verify(query).setParameter("queryLimit", 101);
    }

    @Test
    void differentFilterFingerprintRejectsCursorBeforeQuery() {
        StaffWorkshopFilter filter = StaffWorkshopFilter.empty();
        String cursor = KeysetCursorCodec.encode(
            new KeysetCursor(
                workshopId,
                "startsAt",
                startsAt.toString(),
                null,
                "different"
            )
        );

        assertThatThrownBy(() ->
            adapter.findAssignedWorkshopsByStaffUserId(
                staffId,
                cursor,
                20,
                filter
            )
        ).isInstanceOf(BadRequestException.class);
        verify(entityManager, never()).createNativeQuery(anyString());
    }

    private Object[] row(UUID id, Instant start) {
        return new Object[] {
            id,
            "Python",
            "python",
            "Description",
            null,
            "Room",
            Timestamp.from(start),
            Timestamp.from(start.plusSeconds(3600)),
            "PUBLISHED",
            "Faculty",
            Timestamp.from(assignedAt),
        };
    }
}
