package com.unihub.workshop.workshops.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.*;

import com.unihub.workshop.shared.exception.BadRequestException;
import com.unihub.workshop.workshops.domain.model.CursorPage;
import com.unihub.workshop.workshops.domain.model.IngestionJobReport;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestConstructor;

@SpringBootTest(
    classes = com.unihub.workshop.WorkshopApplication.class,
    properties = {
        "spring.flyway.enabled=false",
        "datasource.primary.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1",
        "datasource.replica.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1",
        "datasource.primary.driver-class-name=org.h2.Driver",
        "datasource.replica.driver-class-name=org.h2.Driver",
    }
)
@Import(IngestionJobReportRepositoryAdapter.class)
@ActiveProfiles("test")
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@RequiredArgsConstructor
class IngestionJobReportRepositoryAdapterTest {

    private final IngestionJobReportJpaRepository jpaRepository;
    private final IngestionJobReportRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        jpaRepository.deleteAll();
    }

    @Test
    void findByOrganizationId_returnsOnlyOrgItems() {
        UUID org1 = UUID.randomUUID();
        UUID org2 = UUID.randomUUID();

        createEntity(org1, Instant.now());
        createEntity(org1, Instant.now().minus(1, ChronoUnit.HOURS));
        createEntity(org2, Instant.now());

        CursorPage<IngestionJobReport> page = adapter.findByOrganizationId(
            org1,
            null,
            10
        );

        assertEquals(2, page.items().size());
        page.items().forEach(item -> assertEquals(org1, item.organizationId()));
    }

    @Test
    void findByOrganizationId_paginatesCorrectlyWithCursor() {
        UUID orgId = UUID.randomUUID();
        Instant baseTime = Instant.now();

        // Create 5 entities with descending timestamps
        IngestionJobReportJpaEntity e1 = createEntity(orgId, baseTime);
        IngestionJobReportJpaEntity e2 = createEntity(
            orgId,
            baseTime.minusSeconds(10)
        );
        IngestionJobReportJpaEntity e3 = createEntity(
            orgId,
            baseTime.minusSeconds(20)
        );
        IngestionJobReportJpaEntity e4 = createEntity(
            orgId,
            baseTime.minusSeconds(30)
        );
        IngestionJobReportJpaEntity e5 = createEntity(
            orgId,
            baseTime.minusSeconds(40)
        );

        // First page (size 2) -> returns e1, e2
        CursorPage<IngestionJobReport> page1 = adapter.findByOrganizationId(
            orgId,
            null,
            2
        );
        assertEquals(2, page1.items().size());
        assertTrue(page1.hasMore());
        assertNotNull(page1.nextCursor());
        assertEquals(e1.getId(), page1.items().get(0).id());
        assertEquals(e2.getId(), page1.items().get(1).id());

        // Second page (size 2) -> returns e3, e4
        CursorPage<IngestionJobReport> page2 = adapter.findByOrganizationId(
            orgId,
            page1.nextCursor(),
            2
        );
        assertEquals(2, page2.items().size());
        assertTrue(page2.hasMore());
        assertNotNull(page2.nextCursor());
        assertEquals(e3.getId(), page2.items().get(0).id());
        assertEquals(e4.getId(), page2.items().get(1).id());

        // Third page (size 2) -> returns e5
        CursorPage<IngestionJobReport> page3 = adapter.findByOrganizationId(
            orgId,
            page2.nextCursor(),
            2
        );
        assertEquals(1, page3.items().size());
        assertFalse(page3.hasMore());
        assertNull(page3.nextCursor());
        assertEquals(e5.getId(), page3.items().get(0).id());
    }

    @Test
    void findByOrganizationId_invalidCursor_throwsException() {
        UUID orgId = UUID.randomUUID();
        assertThrows(BadRequestException.class, () ->
            adapter.findByOrganizationId(orgId, "invalid-base64-cursor", 10)
        );
    }

    private IngestionJobReportJpaEntity createEntity(UUID orgId, Instant time) {
        IngestionJobReportJpaEntity entity = new IngestionJobReportJpaEntity(
            UUID.randomUUID(),
            null,
            orgId,
            "file.csv",
            "COMPLETED",
            100,
            0,
            null,
            null,
            time,
            time,
            time
        );
        return jpaRepository.save(entity);
    }
}
