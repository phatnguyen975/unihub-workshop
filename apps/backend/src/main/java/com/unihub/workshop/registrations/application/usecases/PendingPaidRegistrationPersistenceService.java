package com.unihub.workshop.registrations.application.usecases;

import com.unihub.workshop.registrations.api.RegistrationPaymentPort;
import com.unihub.workshop.registrations.api.dto.RecordPendingPaymentCommand;
import com.unihub.workshop.registrations.application.ports.out.RegistrationRepository;
import com.unihub.workshop.registrations.domain.model.Registration;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists the two local records that establish a paid checkout atomically.
 * The Stripe call deliberately happens before this service is entered.
 */
@Service
@RequiredArgsConstructor
public class PendingPaidRegistrationPersistenceService {

    private final RegistrationRepository registrationRepository;
    private final RegistrationPaymentPort registrationPaymentPort;

    @Transactional
    public Registration persist(
        Registration registration,
        RecordPendingPaymentCommand pendingPayment
    ) {
        Registration saved = registrationRepository.save(registration);
        registrationPaymentPort.recordPendingPayment(pendingPayment);
        return saved;
    }
}
