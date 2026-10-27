package com.unihub.workshop.registrations.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.unihub.workshop.registrations.api.RegistrationPaymentPort;
import com.unihub.workshop.registrations.api.dto.RecordPendingPaymentCommand;
import com.unihub.workshop.registrations.application.ports.out.RegistrationRepository;
import com.unihub.workshop.registrations.domain.model.Registration;
import java.lang.reflect.Method;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.transaction.annotation.Transactional;

class PendingPaidRegistrationPersistenceServiceTest {

    @Test
    void persist_SavesRegistrationBeforeItsPaymentRecord() {
        RegistrationRepository registrations = mock(
            RegistrationRepository.class
        );
        RegistrationPaymentPort payments = mock(RegistrationPaymentPort.class);
        PendingPaidRegistrationPersistenceService service =
            new PendingPaidRegistrationPersistenceService(
                registrations,
                payments
            );
        Registration registration = mock(Registration.class);
        RecordPendingPaymentCommand payment = new RecordPendingPaymentCommand(
            UUID.randomUUID(),
            "pi_123",
            null,
            "USD"
        );
        when(registrations.save(registration)).thenReturn(registration);

        Registration saved = service.persist(registration, payment);

        assertThat(saved).isSameAs(registration);
        InOrder order = inOrder(registrations, payments);
        order.verify(registrations).save(registration);
        order.verify(payments).recordPendingPayment(payment);
    }

    @Test
    void persist_PropagatesPaymentWriteFailureForTransactionRollback() {
        RegistrationRepository registrations = mock(
            RegistrationRepository.class
        );
        RegistrationPaymentPort payments = mock(RegistrationPaymentPort.class);
        PendingPaidRegistrationPersistenceService service =
            new PendingPaidRegistrationPersistenceService(
                registrations,
                payments
            );
        Registration registration = mock(Registration.class);
        RecordPendingPaymentCommand payment = new RecordPendingPaymentCommand(
            UUID.randomUUID(),
            "pi_123",
            null,
            "USD"
        );
        when(registrations.save(any())).thenReturn(registration);
        RuntimeException failure = new RuntimeException(
            "payment persistence failed"
        );
        doThrow(failure).when(payments).recordPendingPayment(payment);

        assertThatThrownBy(() ->
            service.persist(registration, payment)
        ).isSameAs(failure);
    }

    @Test
    void persist_DoesNotCreatePaymentWhenRegistrationCannotBeSaved() {
        RegistrationRepository registrations = mock(
            RegistrationRepository.class
        );
        RegistrationPaymentPort payments = mock(RegistrationPaymentPort.class);
        PendingPaidRegistrationPersistenceService service =
            new PendingPaidRegistrationPersistenceService(
                registrations,
                payments
            );
        Registration registration = mock(Registration.class);
        RecordPendingPaymentCommand payment = new RecordPendingPaymentCommand(
            UUID.randomUUID(),
            "pi_123",
            null,
            "USD"
        );
        RuntimeException failure = new RuntimeException(
            "registration persistence failed"
        );
        when(registrations.save(registration)).thenThrow(failure);

        assertThatThrownBy(() ->
            service.persist(registration, payment)
        ).isSameAs(failure);

        verifyNoInteractions(payments);
    }

    @Test
    void persist_IsTransactional() throws NoSuchMethodException {
        Method method =
            PendingPaidRegistrationPersistenceService.class.getMethod(
                "persist",
                Registration.class,
                RecordPendingPaymentCommand.class
            );

        assertThat(method.isAnnotationPresent(Transactional.class)).isTrue();
    }
}
