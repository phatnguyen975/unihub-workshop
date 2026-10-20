package com.unihub.workshop.registrations.application.strategy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.unihub.workshop.iam.api.dto.StudentProfileDto;
import com.unihub.workshop.registrations.api.RegistrationPaymentPort;
import com.unihub.workshop.registrations.api.dto.CreatePaymentIntentCommand;
import com.unihub.workshop.registrations.api.dto.PaymentIntentResult;
import com.unihub.workshop.registrations.api.dto.RecordPendingPaymentCommand;
import com.unihub.workshop.registrations.application.usecases.PendingPaidRegistrationPersistenceService;
import com.unihub.workshop.registrations.domain.model.Registration;
import com.unihub.workshop.registrations.domain.model.RegistrationStatus;
import com.unihub.workshop.workshops.api.dto.WorkshopRegistrationDetailsDto;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class PaidRegistrationStrategyTest {

    private RegistrationPaymentPort registrationPaymentPort;
    private PendingPaidRegistrationPersistenceService persistenceService;
    private PaidRegistrationStrategy strategy;

    private final UUID studentUserId = UUID.randomUUID();
    private final UUID workshopId = UUID.randomUUID();
    private final UUID idempotencyKeyId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        registrationPaymentPort = mock(RegistrationPaymentPort.class);
        persistenceService = mock(
            PendingPaidRegistrationPersistenceService.class
        );

        strategy = new PaidRegistrationStrategy(
            registrationPaymentPort,
            persistenceService,
            30L
        );
    }

    private StudentProfileDto validStudent() {
        return new StudentProfileDto(
            studentUserId,
            UUID.randomUUID(),
            "John Doe",
            "john@uni.edu"
        );
    }

    private WorkshopRegistrationDetailsDto paidWorkshop(BigDecimal price) {
        return new WorkshopRegistrationDetailsDto(
            workshopId,
            UUID.randomUUID(),
            "Advanced Cloud Architecture",
            "advanced-cloud-architecture",
            "PUBLISHED",
            Instant.now().plus(2, ChronoUnit.HOURS),
            Instant.now().plus(4, ChronoUnit.HOURS),
            price,
            50,
            "Lab 202"
        );
    }

    @Test
    @DisplayName("supports: returns true for workshop with price > 0")
    void supports_PriceGreaterThanZero_ReturnsTrue() {
        WorkshopRegistrationDetailsDto workshop = paidWorkshop(
            new BigDecimal("50.00")
        );
        assertThat(strategy.supports(workshop)).isTrue();
    }

    @Test
    @DisplayName("supports: returns false for workshop with price == 0 or null")
    void supports_PriceZeroOrNull_ReturnsFalse() {
        assertThat(strategy.supports(paidWorkshop(BigDecimal.ZERO))).isFalse();
        assertThat(strategy.supports(paidWorkshop(null))).isFalse();
        assertThat(strategy.supports(null)).isFalse();
    }

    @Test
    @DisplayName(
        "register: creates payment intent, saves PENDING registration, and returns response with clientSecret"
    )
    void register_PaidWorkshop_CreatesPaymentIntentAndSavesPendingRegistration() {
        StudentProfileDto student = validStudent();
        WorkshopRegistrationDetailsDto workshop = paidWorkshop(
            new BigDecimal("50.00")
        );

        PaymentIntentResult paymentResult = new PaymentIntentResult(
            "pi_test_123456",
            "pi_test_123456_secret_987654",
            "requires_payment_method"
        );
        when(
            registrationPaymentPort.createPaymentIntent(
                any(CreatePaymentIntentCommand.class)
            )
        ).thenReturn(paymentResult);

        when(
            persistenceService.persist(any(Registration.class), any())
        ).thenAnswer(invocation -> invocation.getArgument(0));

        Registration result = strategy.register(
            student,
            workshop,
            idempotencyKeyId
        );

        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo(RegistrationStatus.PENDING);
        assertThat(result.priceAtRegistration()).isEqualByComparingTo("50.00");
        assertThat(result.currency()).isEqualTo("USD");
        assertThat(result.paymentClientSecret()).isEqualTo(
            "pi_test_123456_secret_987654"
        );
        assertThat(result.qrTicket()).isNull();
        assertThat(result.expiresAt()).isNotNull();
        assertThat(result.expiresAt()).isAfter(Instant.now());

        ArgumentCaptor<CreatePaymentIntentCommand> cmdCaptor =
            ArgumentCaptor.forClass(CreatePaymentIntentCommand.class);
        verify(registrationPaymentPort).createPaymentIntent(
            cmdCaptor.capture()
        );

        CreatePaymentIntentCommand capturedCmd = cmdCaptor.getValue();
        assertThat(capturedCmd.workshopId()).isEqualTo(workshopId);
        assertThat(capturedCmd.amount()).isEqualByComparingTo("50.00");
        assertThat(capturedCmd.currency()).isEqualTo("USD");
        assertThat(capturedCmd.idempotencyKey()).isEqualTo(
            idempotencyKeyId.toString()
        );

        ArgumentCaptor<Registration> regCaptor = ArgumentCaptor.forClass(
            Registration.class
        );
        verify(persistenceService).persist(regCaptor.capture(), any());

        Registration capturedReg = regCaptor.getValue();
        assertThat(capturedReg.workshopId()).isEqualTo(workshopId);
        assertThat(capturedReg.studentUserId()).isEqualTo(studentUserId);
        assertThat(capturedReg.status()).isEqualTo(RegistrationStatus.PENDING);
        assertThat(capturedReg.idempotencyKeyId()).isEqualTo(idempotencyKeyId);
        assertThat(capturedReg.qrTicket()).isNull();

        // Verify the payment persistence command shares the atomic persistence boundary.
        ArgumentCaptor<RecordPendingPaymentCommand> pendingPaymentCaptor =
            ArgumentCaptor.forClass(RecordPendingPaymentCommand.class);
        verify(persistenceService).persist(
            any(Registration.class),
            pendingPaymentCaptor.capture()
        );
        RecordPendingPaymentCommand capturedPendingCmd =
            pendingPaymentCaptor.getValue();
        assertThat(capturedPendingCmd.providerPaymentId()).isEqualTo(
            "pi_test_123456"
        );
        assertThat(capturedPendingCmd.amount()).isEqualByComparingTo("50.00");
        assertThat(capturedPendingCmd.currency()).isEqualTo("USD");
    }
}
