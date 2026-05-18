package com.unihub.workshop.registrations.service;

import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;
import com.unihub.workshop.registrations.domain.Payment;
import com.unihub.workshop.registrations.domain.PaymentStatus;
import com.unihub.workshop.registrations.domain.Registration;
import com.unihub.workshop.registrations.repository.PaymentRepository;
import com.unihub.workshop.registrations.repository.RegistrationRepository;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.unihub.workshop.registrations.domain.events.RegistrationConfirmedEvent;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@Slf4j
public class PaymentService {

    private final RegistrationRepository registrationRepository;
    private final PaymentRepository paymentRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${app.stripe.secret-key:}")
    private String stripeSecretKey;

    public PaymentService(RegistrationRepository registrationRepository, PaymentRepository paymentRepository,
            ApplicationEventPublisher eventPublisher) {
        this.registrationRepository = registrationRepository;
        this.paymentRepository = paymentRepository;
        this.eventPublisher = eventPublisher;
    }

    @CircuitBreaker(name = "stripePayment", fallbackMethod = "stripeFallback")
    @Transactional
    public Session initiatePayment(UUID registrationId, UUID userId) throws StripeException {
        Registration reg = registrationRepository.findById(registrationId)
                .orElseThrow(() -> new IllegalArgumentException("Registration not found"));

        if (!reg.getUserId().equals(userId)) {
            throw new SecurityException("Not owner");
        }

        if (!"PENDING".equals(reg.getStatus())) {
            throw new IllegalStateException("Registration not in PENDING state");
        }

        // Initialize Stripe
        Stripe.apiKey = stripeSecretKey;

        // Build checkout session
        SessionCreateParams params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setSuccessUrl("https://example.com/success")
                .setCancelUrl("https://example.com/cancel")
                .addLineItem(SessionCreateParams.LineItem.builder()
                        .setQuantity(1L)
                        .setPriceData(SessionCreateParams.LineItem.PriceData.builder()
                                .setCurrency(reg.getWorkshop().getCurrency() == null ? "usd"
                                        : reg.getWorkshop().getCurrency())
                                .setUnitAmount(reg.getWorkshop().getPriceAmount()
                                        .multiply(new java.math.BigDecimal(100)).longValue())
                                .setProductData(SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                        .setName(reg.getWorkshop().getTitle())
                                        .build())
                                .build())
                        .build())
                .putMetadata("registration_id", reg.getId().toString())
                .build();

        Session session = Session.create(params);

        // persist payment
        Payment p = new Payment();
        p.setRegistrationId(reg.getId());
        p.setProvider("stripe");
        p.setProviderRef(session.getId());
        p.setStatus(PaymentStatus.PENDING);
        p.setAmount(reg.getWorkshop().getPriceAmount());
        p.setCurrency(reg.getWorkshop().getCurrency());
        p.setProviderPayload(session.toJson());
        paymentRepository.save(p);

        return session;
    }

    // Fallback when circuit breaker open
    public Session stripeFallback(UUID registrationId, UUID userId, Throwable t) {
        throw new RuntimeException("PaymentService unavailable", t);
    }

    @Transactional
    public void finalizePaymentForSession(String sessionId) {
        log.info("Entering finalizePaymentForSession with providerRef: {}", sessionId);
        var opt = paymentRepository.findByProviderRef(sessionId);
        if (opt.isEmpty()) {
            log.error("CRITICAL: No payment found in DB for providerRef: {}", sessionId);
            throw new IllegalStateException("Payment not found for providerRef=" + sessionId);
        }

        var payment = opt.get();
        log.info("Payment record found. Current status: {}", payment.getStatus());

        if (PaymentStatus.SUCCESS.equals(payment.getStatus())) {
            log.info("Stripe payment already successful for providerRef={} paymentId={}", sessionId, payment.getId());
            return;
        }

        log.info("Looking up registration for paymentId={} registrationId={}", payment.getId(), payment.getRegistrationId());
        var regOpt = registrationRepository.findById(payment.getRegistrationId());
        if (regOpt.isEmpty()) {
            log.warn("Registration NOT FOUND for paymentId={} registrationId={}", payment.getId(),
                    payment.getRegistrationId());
            throw new IllegalStateException("Registration not found for paymentId=" + payment.getId());
        }

        var reg = regOpt.get();
        log.info("Registration record found. Current status: {}", reg.getStatus());

        payment.setStatus(PaymentStatus.SUCCESS);
        log.info("Attempting to save updated entities to DB.");
        paymentRepository.save(payment);
        log.info("Updated payment status to SUCCESS and saved paymentId={} providerRef={}", payment.getId(),
                sessionId);

        reg.setStatus("CONFIRMED");
        log.info("Attempting to save updated entities to DB.");
        registrationRepository.save(reg);
        log.info("Updated registration status to CONFIRMED and saved registrationId={}", reg.getId());

        // publish event after commit
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                eventPublisher.publishEvent(
                        new RegistrationConfirmedEvent(reg.getId(), reg.getUserId(), reg.getWorkshop().getId()));
            }
        });
    }
}
