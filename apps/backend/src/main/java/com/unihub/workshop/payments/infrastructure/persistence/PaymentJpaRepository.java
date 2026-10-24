package com.unihub.workshop.payments.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PaymentJpaRepository
    extends JpaRepository<PaymentJpaEntity, UUID>
{
    /**
     * Read-only projection of this module's own columns. Joined tables appear only in the
     * {@code JOIN} and {@code WHERE} clauses and contribute only tenant visibility.
     */
    String ORGANIZATION_SUMMARY_SQL = """
    SELECT p.currency, p.status, COUNT(*), SUM(p.amount)
    FROM payments p
    JOIN registrations r ON r.id = p.registration_id
    JOIN workshops w ON w.id = r.workshop_id
    WHERE w.organization_id = :organizationId
    GROUP BY p.currency, p.status
    ORDER BY p.currency ASC, p.status ASC
    """;

    Optional<PaymentJpaEntity> findByProviderPaymentId(
        String providerPaymentId
    );

    Optional<PaymentJpaEntity> findByRegistrationId(UUID registrationId);

    boolean existsByProviderPaymentId(String providerPaymentId);

    @Query(value = ORGANIZATION_SUMMARY_SQL, nativeQuery = true)
    List<Object[]> summarizeByOrganizationId(
        @Param("organizationId") UUID organizationId
    );
}
