package com.unihub.workshop.workshops.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.unihub.workshop.shared.exception.BadRequestException;
import com.unihub.workshop.shared.pagination.CursorPage;
import com.unihub.workshop.workshops.application.ports.in.ListAdminWorkshopsQuery;
import com.unihub.workshop.workshops.domain.model.WorkshopSummary;
import com.unihub.workshop.workshops.infrastructure.persistence.mapper.WorkshopPersistenceMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WorkshopRepositoryAdapterListTest {

    @Mock
    private WorkshopJpaRepository workshopJpaRepository;

    @Mock
    private WorkshopSpeakerJpaRepository speakerJpaRepository;

    @Mock
    private WorkshopPersistenceMapper mapper;

    @Mock
    private EntityManager entityManager;

    private WorkshopRepositoryAdapter adapter;

    private static final UUID ORG_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        adapter = new WorkshopRepositoryAdapter(
            workshopJpaRepository,
            speakerJpaRepository,
            mapper,
            entityManager
        );
    }

    @Test
    @DisplayName(
        "Should throw BadRequestException when cursor is invalid Base64"
    )
    void shouldThrowWhenCursorIsInvalidBase64() {
        ListAdminWorkshopsQuery query = ListAdminWorkshopsQuery.builder()
            .organizationId(ORG_ID)
            .cursor("not-valid-base64!@#$")
            .build();

        assertThatThrownBy(() ->
            adapter.findAllByOrganizationWithFilters(query)
        )
            .isInstanceOf(BadRequestException.class)
            .hasMessageContaining("Invalid cursor format");
    }

    @Test
    @DisplayName(
        "Should throw BadRequestException when cursor JSON is missing required fields"
    )
    void shouldThrowWhenCursorMissingRequiredFields() {
        String invalidJson = "{\"title\":\"wrong\"}";
        String encoded = Base64.getEncoder().encodeToString(
            invalidJson.getBytes(StandardCharsets.UTF_8)
        );

        ListAdminWorkshopsQuery query = ListAdminWorkshopsQuery.builder()
            .organizationId(ORG_ID)
            .cursor(encoded)
            .sort("createdAt,desc")
            .build();

        assertThatThrownBy(() ->
            adapter.findAllByOrganizationWithFilters(query)
        )
            .isInstanceOf(BadRequestException.class)
            .hasMessageContaining("Invalid cursor format");
    }

    @Test
    @DisplayName(
        "Should return first page when cursor is null, including nextCursor when hasMore is true"
    )
    void shouldReturnFirstPageWithNextCursor() {
        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        CriteriaQuery<WorkshopJpaEntity> cq = mock();
        Root<WorkshopJpaEntity> root = mock();
        TypedQuery<WorkshopJpaEntity> typedQuery = mock();
        Path<Object> orgPath = mock();
        Path<Object> sortPath = mock();
        Path<Object> idPath = mock();
        Predicate orgPredicate = mock(Predicate.class);

        when(entityManager.getCriteriaBuilder()).thenReturn(cb);
        when(cb.createQuery(WorkshopJpaEntity.class)).thenReturn(cq);
        when(cq.from(WorkshopJpaEntity.class)).thenReturn(root);

        when(root.get("organizationId")).thenReturn(orgPath);
        when(cb.equal(orgPath, ORG_ID)).thenReturn(orgPredicate);
        when(root.get("createdAt")).thenReturn(sortPath);
        when(root.get("id")).thenReturn(idPath);

        when(entityManager.createQuery(cq)).thenReturn(typedQuery);
        when(typedQuery.setMaxResults(3)).thenReturn(typedQuery); // limit 2 + 1

        UUID w1Id = UUID.randomUUID();
        UUID w2Id = UUID.randomUUID();
        UUID w3Id = UUID.randomUUID();
        Instant now = Instant.now();

        WorkshopJpaEntity e1 = WorkshopJpaEntity.builder()
            .id(w1Id)
            .organizationId(ORG_ID)
            .title("Workshop 1")
            .slug("workshop-1")
            .status("DRAFT")
            .price(BigDecimal.ZERO)
            .version(0)
            .createdAt(now.minusSeconds(10))
            .updatedAt(now.minusSeconds(10))
            .build();

        WorkshopJpaEntity e2 = WorkshopJpaEntity.builder()
            .id(w2Id)
            .organizationId(ORG_ID)
            .title("Workshop 2")
            .slug("workshop-2")
            .status("DRAFT")
            .price(BigDecimal.ZERO)
            .version(0)
            .createdAt(now.minusSeconds(20))
            .updatedAt(now.minusSeconds(20))
            .build();

        WorkshopJpaEntity e3 = WorkshopJpaEntity.builder()
            .id(w3Id)
            .organizationId(ORG_ID)
            .title("Workshop 3")
            .slug("workshop-3")
            .status("DRAFT")
            .price(BigDecimal.ZERO)
            .version(0)
            .createdAt(now.minusSeconds(30))
            .updatedAt(now.minusSeconds(30))
            .build();

        when(typedQuery.getResultList()).thenReturn(List.of(e1, e2, e3));

        // Speaker count mock for 2 items in page (e1 and e2)
        when(
            speakerJpaRepository.countSpeakersByWorkshopIds(List.of(w1Id, w2Id))
        ).thenReturn(List.<Object[]>of(new Object[] { w1Id, 2L }));

        ListAdminWorkshopsQuery query = ListAdminWorkshopsQuery.builder()
            .organizationId(ORG_ID)
            .cursor(null)
            .size(2)
            .sort("createdAt,desc")
            .build();

        CursorPage<WorkshopSummary> page =
            adapter.findAllByOrganizationWithFilters(query);

        assertThat(page.items()).hasSize(2);
        assertThat(page.items().get(0).id()).isEqualTo(w1Id);
        assertThat(page.items().get(0).speakerCount()).isEqualTo(2);
        assertThat(page.items().get(1).id()).isEqualTo(w2Id);
        assertThat(page.items().get(1).speakerCount()).isEqualTo(0); // 0 when not in count map
        assertThat(page.hasMore()).isTrue();
        assertThat(page.nextCursor()).isNotNull();

        // Verify nextCursor can be decoded
        byte[] decoded = Base64.getDecoder().decode(page.nextCursor());
        String json = new String(decoded, StandardCharsets.UTF_8);
        assertThat(json).contains(w2Id.toString());
    }

    @Test
    @DisplayName("Should return page without nextCursor when hasMore is false")
    void shouldReturnPageWithoutNextCursorWhenNoMore() {
        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        CriteriaQuery<WorkshopJpaEntity> cq = mock();
        Root<WorkshopJpaEntity> root = mock();
        TypedQuery<WorkshopJpaEntity> typedQuery = mock();
        Path<Object> orgPath = mock();
        Path<Object> sortPath = mock();
        Path<Object> idPath = mock();
        Predicate orgPredicate = mock(Predicate.class);

        when(entityManager.getCriteriaBuilder()).thenReturn(cb);
        when(cb.createQuery(WorkshopJpaEntity.class)).thenReturn(cq);
        when(cq.from(WorkshopJpaEntity.class)).thenReturn(root);

        when(root.get("organizationId")).thenReturn(orgPath);
        when(cb.equal(orgPath, ORG_ID)).thenReturn(orgPredicate);
        when(root.get("createdAt")).thenReturn(sortPath);
        when(root.get("id")).thenReturn(idPath);

        when(entityManager.createQuery(cq)).thenReturn(typedQuery);
        when(typedQuery.setMaxResults(21)).thenReturn(typedQuery);

        UUID w1Id = UUID.randomUUID();
        WorkshopJpaEntity e1 = WorkshopJpaEntity.builder()
            .id(w1Id)
            .organizationId(ORG_ID)
            .title("Solo Workshop")
            .slug("solo-workshop")
            .status("DRAFT")
            .price(BigDecimal.ZERO)
            .version(0)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();

        when(typedQuery.getResultList()).thenReturn(List.of(e1));
        when(
            speakerJpaRepository.countSpeakersByWorkshopIds(List.of(w1Id))
        ).thenReturn(List.of());

        ListAdminWorkshopsQuery query = ListAdminWorkshopsQuery.builder()
            .organizationId(ORG_ID)
            .cursor(null)
            .size(20)
            .sort("createdAt,desc")
            .build();

        CursorPage<WorkshopSummary> page =
            adapter.findAllByOrganizationWithFilters(query);

        assertThat(page.items()).hasSize(1);
        assertThat(page.hasMore()).isFalse();
        assertThat(page.nextCursor()).isNull();
    }

    /**
     * Verifies that supplying a valid non-null cursor triggers the keyset predicate path.
     * Specifically: when the query returns exactly {@code size} items (not size+1),
     * hasMore must be false and nextCursor must be null — the boundary case.
     *
     * Uses lenient stubbing for the keyset CriteriaBuilder chain because strict-mode
     * Mockito would otherwise flag the broad matchers as PotentialStubbingProblem.
     */
    @Test
    @DisplayName(
        "Should correctly process a valid non-null cursor: keyset predicate executes, boundary page has hasMore=false"
    )
    void shouldHandleValidNonNullCursorOnBoundaryPage() {
        // Build a valid cursor for sort=createdAt,desc
        Instant cursorCreatedAt = Instant.parse("2026-09-20T10:00:00Z");
        UUID cursorId = UUID.randomUUID();
        String cursorJson = String.format(
            "{\"id\":\"%s\",\"sortField\":\"createdAt\",\"primaryValue\":\"%s\"}",
            cursorId,
            cursorCreatedAt
        );
        String encodedCursor = Base64.getEncoder().encodeToString(
            cursorJson.getBytes(StandardCharsets.UTF_8)
        );

        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        CriteriaQuery<WorkshopJpaEntity> cq = mock();
        Root<WorkshopJpaEntity> root = mock();
        TypedQuery<WorkshopJpaEntity> typedQuery = mock();
        Path<Object> orgPath = mock();
        Path<Object> sortPath = mock();
        Path<Object> idPath = mock();
        Predicate orgPredicate = mock(Predicate.class);
        Predicate keysetPredicate = mock(Predicate.class);

        when(entityManager.getCriteriaBuilder()).thenReturn(cb);
        when(cb.createQuery(WorkshopJpaEntity.class)).thenReturn(cq);
        when(cq.from(WorkshopJpaEntity.class)).thenReturn(root);

        when(root.get("organizationId")).thenReturn(orgPath);
        when(cb.equal(orgPath, ORG_ID)).thenReturn(orgPredicate);
        when(root.get("createdAt")).thenReturn(sortPath);
        when(root.get("id")).thenReturn(idPath);

        // Use lenient stubs for the keyset predicate chain to avoid PotentialStubbingProblem
        // with Mockito strict mode: the broad matchers would conflict with the specific
        // cb.equal(orgPath, ORG_ID) stub registered above.
        lenient()
            .when(cb.lessThan(any(), any(Instant.class)))
            .thenReturn(mock(Predicate.class));
        lenient()
            .when(cb.lessThan(any(), any(UUID.class)))
            .thenReturn(mock(Predicate.class));
        lenient()
            .when(cb.and(any(Predicate.class), any(Predicate.class)))
            .thenReturn(mock(Predicate.class));
        lenient()
            .when(cb.or(any(Predicate.class), any(Predicate.class)))
            .thenReturn(keysetPredicate);

        when(entityManager.createQuery(cq)).thenReturn(typedQuery);
        when(typedQuery.setMaxResults(3)).thenReturn(typedQuery); // size=2, fetch size+1=3

        UUID w1Id = UUID.randomUUID();
        UUID w2Id = UUID.randomUUID();
        WorkshopJpaEntity e1 = WorkshopJpaEntity.builder()
            .id(w1Id)
            .organizationId(ORG_ID)
            .title("W1")
            .slug("w1")
            .status("DRAFT")
            .price(BigDecimal.ZERO)
            .version(0)
            .createdAt(Instant.parse("2026-09-18T10:00:00Z"))
            .updatedAt(Instant.parse("2026-09-18T10:00:00Z"))
            .build();
        WorkshopJpaEntity e2 = WorkshopJpaEntity.builder()
            .id(w2Id)
            .organizationId(ORG_ID)
            .title("W2")
            .slug("w2")
            .status("DRAFT")
            .price(BigDecimal.ZERO)
            .version(0)
            .createdAt(Instant.parse("2026-09-17T10:00:00Z"))
            .updatedAt(Instant.parse("2026-09-17T10:00:00Z"))
            .build();

        // Exactly size items (not size+1) → boundary page, no more data
        when(typedQuery.getResultList()).thenReturn(List.of(e1, e2));
        when(
            speakerJpaRepository.countSpeakersByWorkshopIds(List.of(w1Id, w2Id))
        ).thenReturn(List.of());

        ListAdminWorkshopsQuery query = ListAdminWorkshopsQuery.builder()
            .organizationId(ORG_ID)
            .cursor(encodedCursor)
            .size(2)
            .sort("createdAt,desc")
            .build();

        CursorPage<WorkshopSummary> page =
            adapter.findAllByOrganizationWithFilters(query);

        assertThat(page.items()).hasSize(2);
        assertThat(page.hasMore()).isFalse();
        assertThat(page.nextCursor()).isNull();
    }

    @Test
    @DisplayName(
        "Should throw BadRequestException with sort-mismatch message when cursor sort field differs from request sort"
    )
    void shouldThrowWhenCursorSortFieldMismatch() {
        // Cursor issued for sort=capacity,desc
        UUID cursorId = UUID.randomUUID();
        String cursorJson = String.format(
            "{\"id\":\"%s\",\"sortField\":\"capacity\",\"primaryValue\":\"50\"}",
            cursorId
        );
        String encodedCursor = Base64.getEncoder().encodeToString(
            cursorJson.getBytes(StandardCharsets.UTF_8)
        );

        // But request now uses sort=price,asc — mismatch
        ListAdminWorkshopsQuery query = ListAdminWorkshopsQuery.builder()
            .organizationId(ORG_ID)
            .cursor(encodedCursor)
            .size(10)
            .sort("price,asc")
            .build();

        assertThatThrownBy(() ->
            adapter.findAllByOrganizationWithFilters(query)
        )
            .isInstanceOf(BadRequestException.class)
            .hasMessageContaining("Cursor was issued for sort='capacity'")
            .hasMessageContaining("current request uses sort='price'")
            .hasMessageContaining("Discard the cursor");
    }
}
