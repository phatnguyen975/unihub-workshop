package com.unihub.workshop.workshops.application.usecases;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.unihub.workshop.shared.exception.AppException;
import com.unihub.workshop.workshops.application.ports.out.IngestionJobReportRepository;
import com.unihub.workshop.workshops.domain.model.CursorPage;
import com.unihub.workshop.workshops.domain.model.IngestionJobReport;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ListIngestionJobsUseCaseImplImplTest {

    private IngestionJobReportRepository repository;
    private ListIngestionJobsUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        repository = mock(IngestionJobReportRepository.class);
        useCase = new ListIngestionJobsUseCaseImpl(repository);
    }

    @Test
    void execute_withNullOrganizationId_throwsException() {
        AppException ex = assertThrows(AppException.class, () ->
            useCase.execute(null, null, 20)
        );
        assertEquals("organizationId must not be null", ex.getMessage());
    }

    @Test
    void execute_withSizeLessThanOne_throwsException() {
        UUID orgId = UUID.randomUUID();
        AppException ex = assertThrows(AppException.class, () ->
            useCase.execute(orgId, null, 0)
        );
        assertEquals("size must be at least 1", ex.getMessage());
    }

    @Test
    void execute_withSizeGreaterThan100_clampsTo100() {
        UUID orgId = UUID.randomUUID();
        CursorPage<IngestionJobReport> expected = new CursorPage<>(
            List.of(),
            null,
            false
        );
        when(repository.findByOrganizationId(orgId, null, 100)).thenReturn(
            expected
        );

        CursorPage<IngestionJobReport> result = useCase.execute(
            orgId,
            null,
            500
        );

        assertSame(expected, result);
        verify(repository).findByOrganizationId(orgId, null, 100);
    }

    @Test
    void execute_withNullSize_defaultsTo20() {
        UUID orgId = UUID.randomUUID();
        CursorPage<IngestionJobReport> expected = new CursorPage<>(
            List.of(),
            null,
            false
        );
        when(repository.findByOrganizationId(orgId, null, 20)).thenReturn(
            expected
        );

        CursorPage<IngestionJobReport> result = useCase.execute(
            orgId,
            null,
            null
        );

        assertSame(expected, result);
        verify(repository).findByOrganizationId(orgId, null, 20);
    }

    @Test
    void execute_withValidParameters_returnsPage() {
        UUID orgId = UUID.randomUUID();
        String cursor = "test-cursor";
        CursorPage<IngestionJobReport> expected = new CursorPage<>(
            List.of(),
            "next-cursor",
            true
        );
        when(repository.findByOrganizationId(orgId, cursor, 50)).thenReturn(
            expected
        );

        CursorPage<IngestionJobReport> result = useCase.execute(
            orgId,
            cursor,
            50
        );

        assertSame(expected, result);
        verify(repository).findByOrganizationId(orgId, cursor, 50);
    }
}
