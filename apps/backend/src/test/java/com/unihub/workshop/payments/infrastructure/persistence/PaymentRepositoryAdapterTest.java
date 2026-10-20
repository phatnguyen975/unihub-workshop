package com.unihub.workshop.payments.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.unihub.workshop.payments.domain.model.Payment;
import com.unihub.workshop.payments.domain.model.PaymentStatus;
import com.unihub.workshop.payments.infrastructure.persistence.mapper.PaymentPersistenceMapper;
import com.unihub.workshop.payments.infrastructure.persistence.mapper.PaymentPersistenceMapperImpl;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PaymentRepositoryAdapterTest {

    private PaymentJpaRepository jpaRepository;
    private final PaymentPersistenceMapper mapper =
        new PaymentPersistenceMapperImpl();
    private PaymentRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        jpaRepository = mock(PaymentJpaRepository.class);
        adapter = new PaymentRepositoryAdapter(jpaRepository, mapper);
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
}
