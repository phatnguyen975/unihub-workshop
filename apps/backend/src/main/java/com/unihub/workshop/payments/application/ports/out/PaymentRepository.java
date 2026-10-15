package com.unihub.workshop.payments.application.ports.out;

import com.unihub.workshop.payments.domain.model.Payment;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository {
    Payment save(Payment payment);

    Optional<Payment> findByProviderPaymentId(String providerPaymentId);

    Optional<Payment> findByRegistrationId(UUID registrationId);

    boolean existsByProviderPaymentId(String providerPaymentId);
}
