package com.unihub.workshop.payments.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.unihub.workshop.payments.domain.model.Payment;
import com.unihub.workshop.payments.domain.model.PaymentStatus;
import com.unihub.workshop.payments.infrastructure.persistence.mapper.PaymentPersistenceMapper;
import com.unihub.workshop.payments.infrastructure.persistence.mapper.PaymentPersistenceMapperImpl;
import com.unihub.workshop.shared.exception.BadRequestException;
import com.unihub.workshop.shared.pagination.CursorPage;
import com.unihub.workshop.shared.pagination.KeysetCursor;
import com.unihub.workshop.shared.pagination.KeysetCursorCodec;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class PaymentRepositoryAdapterTest {

    private PaymentJpaRepository jpaRepository;
    private final PaymentPersistenceMapper mapper =
        new PaymentPersistenceMapperImpl();
    private EntityManager entityManager;
    private Query nativeQuery;
    private PaymentRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        jpaRepository = mock(PaymentJpaRepository.class);
        entityManager = mock(EntityManager.class);
        nativeQuery = mock(Query.class);
        adapter = new PaymentRepositoryAdapter(
            jpaRepository,
            mapper,
            entityManager
        );
        when(
            entityManager.createNativeQuery(
                anyString(),
                eq(PaymentJpaEntity.class)
            )
        ).thenReturn(nativeQuery);
    }

    @Test
    void shouldSaveAndMapPayment() {
        UUID paymentId = UUID.randomUUID();
        UUID regId = UUID.randomUUID();
        Instant now = Instant.now();

        Payment payment = Payment.builder()
            .id(paymentId)
            .registrationId(regId)
            .provider("STRIPE")
            .providerPaymentId("pi_123")
            .providerRefundId("re_123")
            .status(PaymentStatus.COMPLETED)
            .amount(BigDecimal.valueOf(25.00))
            .currency("USD")
            .completedAt(now)
            .refundedAt(now)
            .createdAt(now)
            .updatedAt(now)
            .build();

        PaymentJpaEntity savedEntity = PaymentJpaEntity.builder()
            .id(paymentId)
            .registrationId(regId)
            .provider("STRIPE")
            .providerPaymentId("pi_123")
            .providerRefundId("re_123")
            .status(PaymentStatus.COMPLETED)
            .amount(BigDecimal.valueOf(25.00))
            .currency("USD")
            .completedAt(now)
            .refundedAt(now)
            .createdAt(now)
            .updatedAt(now)
            .build();

        when(jpaRepository.save(any(PaymentJpaEntity.class))).thenReturn(
            savedEntity
        );

        Payment result = adapter.save(payment);

        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(paymentId);
        assertThat(result.registrationId()).isEqualTo(regId);
        assertThat(result.providerPaymentId()).isEqualTo("pi_123");
        assertThat(result.providerRefundId()).isEqualTo("re_123");
        assertThat(result.status()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(result.amount()).isEqualByComparingTo(
            BigDecimal.valueOf(25.00)
        );
        assertThat(result.currency()).isEqualTo("USD");
        assertThat(result.refundedAt()).isEqualTo(now);
        verify(jpaRepository).save(any(PaymentJpaEntity.class));
    }

    @Test
    void shouldFindByProviderPaymentId() {
        UUID paymentId = UUID.randomUUID();
        UUID regId = UUID.randomUUID();
        Instant now = Instant.now();

        PaymentJpaEntity entity = PaymentJpaEntity.builder()
            .id(paymentId)
            .registrationId(regId)
            .provider("STRIPE")
            .providerPaymentId("pi_abc")
            .status(PaymentStatus.COMPLETED)
            .amount(BigDecimal.valueOf(50.00))
            .currency("USD")
            .createdAt(now)
            .updatedAt(now)
            .build();

        when(jpaRepository.findByProviderPaymentId("pi_abc")).thenReturn(
            Optional.of(entity)
        );

        Optional<Payment> result = adapter.findByProviderPaymentId("pi_abc");

        assertThat(result).isPresent();
        assertThat(result.get().id()).isEqualTo(paymentId);
        assertThat(result.get().providerPaymentId()).isEqualTo("pi_abc");
    }

    @Test
    void shouldReturnEmptyWhenProviderPaymentIdNotFound() {
        when(jpaRepository.findByProviderPaymentId("pi_unknown")).thenReturn(
            Optional.empty()
        );

        Optional<Payment> result = adapter.findByProviderPaymentId(
            "pi_unknown"
        );

        assertThat(result).isEmpty();
    }

    @Test
    void shouldFindByRegistrationId() {
        UUID paymentId = UUID.randomUUID();
        UUID regId = UUID.randomUUID();
        Instant now = Instant.now();

        PaymentJpaEntity entity = PaymentJpaEntity.builder()
            .id(paymentId)
            .registrationId(regId)
            .provider("STRIPE")
            .providerPaymentId("pi_xyz")
            .status(PaymentStatus.FAILED)
            .amount(BigDecimal.valueOf(10.00))
            .currency("USD")
            .failureCode("card_declined")
            .failureReason("Insufficient funds")
            .createdAt(now)
            .updatedAt(now)
            .build();

        when(jpaRepository.findByRegistrationId(regId)).thenReturn(
            Optional.of(entity)
        );

        Optional<Payment> result = adapter.findByRegistrationId(regId);

        assertThat(result).isPresent();
        assertThat(result.get().status()).isEqualTo(PaymentStatus.FAILED);
        assertThat(result.get().failureCode()).isEqualTo("card_declined");
        assertThat(result.get().failureReason()).isEqualTo(
            "Insufficient funds"
        );
    }

    @Test
    void shouldCheckExistsByProviderPaymentId() {
        when(jpaRepository.existsByProviderPaymentId("pi_exists")).thenReturn(
            true
        );
        when(
            jpaRepository.existsByProviderPaymentId("pi_not_exists")
        ).thenReturn(false);

        assertThat(adapter.existsByProviderPaymentId("pi_exists")).isTrue();
        assertThat(
            adapter.existsByProviderPaymentId("pi_not_exists")
        ).isFalse();
    }

    private PaymentJpaEntity paymentEntity(
        UUID paymentId,
        UUID registrationId,
        Instant createdAt
    ) {
        return PaymentJpaEntity.builder()
            .id(paymentId)
            .registrationId(registrationId)
            .provider("STRIPE")
            .providerPaymentId("pi_" + paymentId)
            .status(PaymentStatus.COMPLETED)
            .amount(BigDecimal.valueOf(50.00))
            .currency("USD")
            .completedAt(createdAt)
            .createdAt(createdAt)
            .updatedAt(createdAt)
            .build();
    }

    @Test
    @DisplayName(
        "Student history projects payments only, joins registrations for ownership, and mints a filter-bound cursor"
    )
    void shouldQueryStudentHistoryWithOwnershipJoinAndFilterBoundCursor() {
        UUID studentUserId = UUID.randomUUID();
        UUID registrationId = UUID.randomUUID();
        Instant newest = Instant.parse("2026-10-21T05:14:00Z");
        Instant middle = Instant.parse("2026-10-21T05:12:00Z");
        Instant oldest = Instant.parse("2026-10-21T05:10:00Z");

        UUID newestId = UUID.randomUUID();
        UUID middleId = UUID.randomUUID();
        UUID oldestId = UUID.randomUUID();

        when(nativeQuery.getResultList()).thenReturn(
            List.of(
                paymentEntity(newestId, registrationId, newest),
                paymentEntity(middleId, registrationId, middle),
                paymentEntity(oldestId, registrationId, oldest)
            )
        );

        CursorPage<Payment> page = adapter.findByStudentUserId(
            studentUserId,
            PaymentStatus.COMPLETED,
            null,
            2
        );

        assertThat(page.items())
            .extracting(Payment::id)
            .containsExactly(newestId, middleId);
        assertThat(page.hasMore()).isTrue();

        KeysetCursor nextCursor = KeysetCursorCodec.decode(
            page.nextCursor(),
            "createdAt",
            "status=COMPLETED"
        );
        assertThat(nextCursor.id()).isEqualTo(middleId);
        assertThat(nextCursor.asInstant()).isEqualTo(middle);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(entityManager).createNativeQuery(
            sql.capture(),
            eq(PaymentJpaEntity.class)
        );

        String statement = sql.getValue();
        String selectList = statement.substring(
            0,
            statement.indexOf("FROM payments")
        );
        assertThat(selectList).contains("SELECT p.id");
        assertThat(selectList).doesNotContain("r.");
        assertThat(statement)
            .contains("JOIN registrations r ON r.id = p.registration_id")
            .contains("r.student_user_id = :studentUserId")
            .contains("p.status = :status")
            .contains("ORDER BY p.created_at DESC, p.id DESC");

        verify(nativeQuery).setParameter("studentUserId", studentUserId);
        verify(nativeQuery).setParameter("status", "COMPLETED");
        verify(nativeQuery).setMaxResults(3);
    }

    @Test
    @DisplayName(
        "Student history applies the row-value keyset predicate and omits the status filter when absent"
    )
    void shouldApplyKeysetPredicateWithoutStatusFilter() {
        UUID studentUserId = UUID.randomUUID();
        UUID cursorId = UUID.randomUUID();
        Instant cursorCreatedAt = Instant.parse("2026-10-21T05:12:00Z");
        String cursor = KeysetCursorCodec.encode(
            new KeysetCursor(
                cursorId,
                "createdAt",
                cursorCreatedAt.toString(),
                null,
                "status=*"
            )
        );

        when(nativeQuery.getResultList()).thenReturn(List.of());

        CursorPage<Payment> page = adapter.findByStudentUserId(
            studentUserId,
            null,
            cursor,
            20
        );

        assertThat(page.items()).isEmpty();
        assertThat(page.nextCursor()).isNull();
        assertThat(page.hasMore()).isFalse();

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(entityManager).createNativeQuery(
            sql.capture(),
            eq(PaymentJpaEntity.class)
        );
        assertThat(sql.getValue())
            .contains("(p.created_at, p.id) < (:cursorCreatedAt, :cursorId)")
            .doesNotContain("p.status = :status");

        verify(nativeQuery).setParameter("cursorCreatedAt", cursorCreatedAt);
        verify(nativeQuery).setParameter("cursorId", cursorId);
        verify(nativeQuery, never()).setParameter(eq("status"), any());
    }

    @Test
    @DisplayName(
        "Student history rejects a cursor issued for a different status filter"
    )
    void shouldRejectCursorBoundToAnotherStatusFilter() {
        String cursor = KeysetCursorCodec.encode(
            new KeysetCursor(
                UUID.randomUUID(),
                "createdAt",
                "2026-10-21T05:12:00Z",
                null,
                "status=COMPLETED"
            )
        );

        assertThatThrownBy(() ->
            adapter.findByStudentUserId(
                UUID.randomUUID(),
                PaymentStatus.FAILED,
                cursor,
                20
            )
        )
            .isInstanceOf(BadRequestException.class)
            .hasMessageContaining("different filter set");

        verifyNoInteractions(entityManager);
    }

    @Test
    @DisplayName(
        "Student history omits the continuation cursor when the window is not exceeded"
    )
    void shouldOmitCursorWhenNoFurtherPageExists() {
        UUID registrationId = UUID.randomUUID();
        when(nativeQuery.getResultList()).thenReturn(
            List.of(
                paymentEntity(
                    UUID.randomUUID(),
                    registrationId,
                    Instant.parse("2026-10-21T05:12:00Z")
                )
            )
        );

        CursorPage<Payment> page = adapter.findByStudentUserId(
            UUID.randomUUID(),
            null,
            null,
            20
        );

        assertThat(page.items()).hasSize(1);
        assertThat(page.hasMore()).isFalse();
        assertThat(page.nextCursor()).isNull();
        verify(nativeQuery).setMaxResults(21);
    }

    @Test
    @DisplayName(
        "Organizer history projects payments only, joins workshops for tenant visibility, and binds both filters into the cursor"
    )
    void shouldQueryOrganizerHistoryWithTenantJoinAndFilterBoundCursor() {
        UUID organizationId = UUID.randomUUID();
        UUID workshopId = UUID.randomUUID();
        UUID registrationId = UUID.randomUUID();
        Instant newest = Instant.parse("2026-10-21T05:14:00Z");
        Instant middle = Instant.parse("2026-10-21T05:12:00Z");
        Instant oldest = Instant.parse("2026-10-21T05:10:00Z");
        UUID newestId = UUID.randomUUID();
        UUID middleId = UUID.randomUUID();
        UUID oldestId = UUID.randomUUID();

        when(nativeQuery.getResultList()).thenReturn(
            List.of(
                paymentEntity(newestId, registrationId, newest),
                paymentEntity(middleId, registrationId, middle),
                paymentEntity(oldestId, registrationId, oldest)
            )
        );

        CursorPage<Payment> page = adapter.findByOrganizationId(
            organizationId,
            workshopId,
            PaymentStatus.COMPLETED,
            null,
            2
        );

        assertThat(page.items())
            .extracting(Payment::id)
            .containsExactly(newestId, middleId);
        assertThat(page.hasMore()).isTrue();

        String expectedFingerprint =
            "status=COMPLETED&workshopId=" + workshopId;
        KeysetCursor nextCursor = KeysetCursorCodec.decode(
            page.nextCursor(),
            "createdAt",
            expectedFingerprint
        );
        assertThat(nextCursor.id()).isEqualTo(middleId);
        assertThat(nextCursor.asInstant()).isEqualTo(middle);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(entityManager).createNativeQuery(
            sql.capture(),
            eq(PaymentJpaEntity.class)
        );

        String statement = sql.getValue();
        String selectList = statement.substring(
            0,
            statement.indexOf("FROM payments")
        );
        assertThat(selectList).contains("SELECT p.id");
        assertThat(selectList).doesNotContain("r.");
        assertThat(selectList).doesNotContain("w.");
        assertThat(statement)
            .contains("JOIN registrations r ON r.id = p.registration_id")
            .contains("JOIN workshops w ON w.id = r.workshop_id")
            .contains("w.organization_id = :organizationId")
            .contains("r.workshop_id = :workshopId")
            .contains("p.status = :status")
            .contains("ORDER BY p.created_at DESC, p.id DESC");

        verify(nativeQuery).setParameter("organizationId", organizationId);
        verify(nativeQuery).setParameter("workshopId", workshopId);
        verify(nativeQuery).setParameter("status", "COMPLETED");
        verify(nativeQuery).setMaxResults(3);
    }

    @Test
    @DisplayName(
        "Organizer history omits optional filters while keeping the cursor bound to their absence"
    )
    void shouldQueryOrganizerHistoryWithoutOptionalFilters() {
        UUID organizationId = UUID.randomUUID();
        UUID registrationId = UUID.randomUUID();
        Instant newest = Instant.parse("2026-10-21T05:14:00Z");
        UUID newestId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();

        when(nativeQuery.getResultList()).thenReturn(
            List.of(
                paymentEntity(newestId, registrationId, newest),
                paymentEntity(secondId, registrationId, newest.minusSeconds(60))
            )
        );

        CursorPage<Payment> page = adapter.findByOrganizationId(
            organizationId,
            null,
            null,
            null,
            1
        );

        assertThat(page.items()).hasSize(1);
        assertThat(page.hasMore()).isTrue();

        KeysetCursor nextCursor = KeysetCursorCodec.decode(
            page.nextCursor(),
            "createdAt",
            "status=*&workshopId=*"
        );
        assertThat(nextCursor.id()).isEqualTo(newestId);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(entityManager).createNativeQuery(
            sql.capture(),
            eq(PaymentJpaEntity.class)
        );
        assertThat(sql.getValue())
            .contains("w.organization_id = :organizationId")
            .doesNotContain("r.workshop_id = :workshopId")
            .doesNotContain("p.status = :status");

        verify(nativeQuery).setParameter("organizationId", organizationId);
        verify(nativeQuery, never()).setParameter(eq("workshopId"), any());
        verify(nativeQuery, never()).setParameter(eq("status"), any());
    }

    @Test
    @DisplayName(
        "Organizer history rejects a cursor issued for a different workshop filter"
    )
    void shouldRejectOrganizerCursorBoundToAnotherWorkshopFilter() {
        String cursor = KeysetCursorCodec.encode(
            new KeysetCursor(
                UUID.randomUUID(),
                "createdAt",
                "2026-10-21T05:12:00Z",
                null,
                "status=*&workshopId=" + UUID.randomUUID()
            )
        );

        assertThatThrownBy(() ->
            adapter.findByOrganizationId(
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                cursor,
                20
            )
        )
            .isInstanceOf(BadRequestException.class)
            .hasMessageContaining("different filter set");

        verifyNoInteractions(entityManager);
    }

    @Test
    @DisplayName(
        "Organizer history rejects a cursor issued for a different status filter"
    )
    void shouldRejectOrganizerCursorBoundToAnotherStatusFilter() {
        UUID workshopId = UUID.randomUUID();
        String cursor = KeysetCursorCodec.encode(
            new KeysetCursor(
                UUID.randomUUID(),
                "createdAt",
                "2026-10-21T05:12:00Z",
                null,
                "status=COMPLETED&workshopId=" + workshopId
            )
        );

        assertThatThrownBy(() ->
            adapter.findByOrganizationId(
                UUID.randomUUID(),
                workshopId,
                PaymentStatus.FAILED,
                cursor,
                20
            )
        )
            .isInstanceOf(BadRequestException.class)
            .hasMessageContaining("different filter set");

        verifyNoInteractions(entityManager);
    }

    @Test
    @DisplayName(
        "Organizer history omits the continuation cursor when the window is not exceeded"
    )
    void shouldOmitOrganizerCursorWhenNoFurtherPageExists() {
        UUID organizationId = UUID.randomUUID();
        UUID registrationId = UUID.randomUUID();
        when(nativeQuery.getResultList()).thenReturn(
            List.of(
                paymentEntity(
                    UUID.randomUUID(),
                    registrationId,
                    Instant.parse("2026-10-21T05:12:00Z")
                )
            )
        );

        CursorPage<Payment> page = adapter.findByOrganizationId(
            organizationId,
            null,
            null,
            null,
            20
        );

        assertThat(page.items()).hasSize(1);
        assertThat(page.hasMore()).isFalse();
        assertThat(page.nextCursor()).isNull();
        verify(nativeQuery).setMaxResults(21);
        verify(nativeQuery).setParameter("organizationId", organizationId);
    }
}
