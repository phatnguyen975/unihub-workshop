package com.unihub.workshop.payments.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PaymentJpaRepository
    extends JpaRepository<PaymentJpaEntity, UUID>
{
    Optional<PaymentJpaEntity> findByProviderPaymentId(
        String providerPaymentId
    );

    Optional<PaymentJpaEntity> findByRegistrationId(UUID registrationId);

    boolean existsByProviderPaymentId(String providerPaymentId);
}
