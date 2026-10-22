package com.unihub.workshop.payments.infrastructure.persistence;

import com.unihub.workshop.payments.application.ports.out.PaymentRepository;
import com.unihub.workshop.payments.domain.model.Payment;
import com.unihub.workshop.payments.domain.model.PaymentStatus;
import com.unihub.workshop.payments.infrastructure.persistence.mapper.PaymentPersistenceMapper;
import com.unihub.workshop.shared.exception.BadRequestException;
import com.unihub.workshop.shared.pagination.CursorPage;
import com.unihub.workshop.shared.pagination.FilterFingerprint;
import com.unihub.workshop.shared.pagination.KeysetCursor;
import com.unihub.workshop.shared.pagination.KeysetCursorCodec;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class PaymentRepositoryAdapter implements PaymentRepository {

    private static final String HISTORY_SORT_FIELD = "createdAt";
    private static final String FILTER_STATUS = "status";

    /**
     * Read-only projection of this module's own columns. Joined tables appear only in the
     * {@code JOIN} and {@code WHERE} clauses: no foreign-module column is selected.
     */
    private static final String STUDENT_HISTORY_SELECT = """
    SELECT p.id, p.registration_id, p.provider, p.provider_payment_id, p.provider_refund_id,
           p.status, p.amount, p.currency, p.completed_at, p.failed_at, p.refunded_at,
           p.failure_code, p.failure_reason, p.created_at, p.updated_at, p.created_by, p.updated_by
    FROM payments p
    JOIN registrations r ON r.id = p.registration_id
    WHERE r.student_user_id = :studentUserId
    """;

    private final PaymentJpaRepository paymentJpaRepository;
    private final PaymentPersistenceMapper mapper;
    private final EntityManager entityManager;

    @Override
    @Transactional
    public Payment save(Payment payment) {
        PaymentJpaEntity entity = mapper.toEntity(payment);
        PaymentJpaEntity saved = paymentJpaRepository.save(entity);
        return mapper.toDomain(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Payment> findByProviderPaymentId(String providerPaymentId) {
        return paymentJpaRepository
            .findByProviderPaymentId(providerPaymentId)
            .map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Payment> findByRegistrationId(UUID registrationId) {
        return paymentJpaRepository
            .findByRegistrationId(registrationId)
            .map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByProviderPaymentId(String providerPaymentId) {
        return paymentJpaRepository.existsByProviderPaymentId(
            providerPaymentId
        );
    }

    @Override
    @Transactional(readOnly = true)
    public CursorPage<Payment> findByStudentUserId(
        UUID studentUserId,
        PaymentStatus status,
        String cursor,
        int size
    ) {
        String filterFingerprint = filterFingerprint(status);
        KeysetCursor cursorData = KeysetCursorCodec.decode(
            cursor,
            HISTORY_SORT_FIELD,
            filterFingerprint
        );
        if (cursorData != null && cursorData.asInstant() == null) {
            throw new BadRequestException("Invalid cursor format");
        }

        StringBuilder statement = new StringBuilder(STUDENT_HISTORY_SELECT);
        if (status != null) {
            statement.append(" AND p.status = :status");
        }
        if (cursorData != null) {
            statement.append(
                " AND (p.created_at, p.id) < (:cursorCreatedAt, :cursorId)"
            );
        }
        statement.append(" ORDER BY p.created_at DESC, p.id DESC");

        Query query = entityManager.createNativeQuery(
            statement.toString(),
            PaymentJpaEntity.class
        );
        query.setParameter("studentUserId", studentUserId);
        if (status != null) {
            query.setParameter("status", status.name());
        }
        if (cursorData != null) {
            query.setParameter("cursorCreatedAt", cursorData.asInstant());
            query.setParameter("cursorId", cursorData.id());
        }
        query.setMaxResults(size + 1);
        List<PaymentJpaEntity> rows = toEntityList(query.getResultList());
        boolean hasMore = rows.size() > size;
        List<PaymentJpaEntity> window = hasMore ? rows.subList(0, size) : rows;

        List<Payment> items = mapper.toDomainList(window);

        String nextCursor = null;
        if (hasMore && !window.isEmpty()) {
            PaymentJpaEntity lastRow = window.get(window.size() - 1);
            nextCursor = KeysetCursorCodec.encode(
                new KeysetCursor(
                    lastRow.getId(),
                    HISTORY_SORT_FIELD,
                    lastRow.getCreatedAt().toString(),
                    null,
                    filterFingerprint
                )
            );
        }

        return new CursorPage<>(items, nextCursor, hasMore);
    }

    private String filterFingerprint(PaymentStatus status) {
        return FilterFingerprint.builder().with(FILTER_STATUS, status).build();
    }

    private List<PaymentJpaEntity> toEntityList(List<?> results) {
        List<PaymentJpaEntity> entities = new ArrayList<>(results.size());
        for (Object result : results) {
            entities.add(PaymentJpaEntity.class.cast(result));
        }
        return entities;
    }
}
