package com.unihub.workshop.registrations.presentation.controller;

import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.Event;
import com.stripe.model.checkout.Session;
import com.stripe.model.StripeObject;
import com.stripe.net.Webhook;
import com.unihub.workshop.registrations.repository.PaymentRepository;
import com.unihub.workshop.registrations.repository.RegistrationRepository;
import com.unihub.workshop.registrations.service.PaymentService;
import com.unihub.workshop.shared.web.ResponseFactory;
import com.unihub.workshop.shared.web.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payments")
@Slf4j
public class PaymentController {

    private final PaymentService paymentService;
    private final ResponseFactory responseFactory;

    @Value("${app.stripe.webhook-secret:}")
    private String stripeWebhookSecret;

    public PaymentController(PaymentService paymentService,
            PaymentRepository paymentRepository,
            RegistrationRepository registrationRepository,
            ResponseFactory responseFactory) {
        this.paymentService = paymentService;
        this.responseFactory = responseFactory;
    }

    public static record InitiateRequest(UUID registrationId) {
    }

    @PostMapping
    public ResponseEntity<ApiResponse<?>> initiate(@RequestBody InitiateRequest req, Authentication authentication) {
        UUID userId = UUID.fromString(authentication.getName());
        try {
            Session session = paymentService.initiatePayment(req.registrationId(), userId);
            return ResponseEntity
                    .ok(responseFactory.success(Map.of("checkoutUrl", session.getUrl()), "Checkout created"));
        } catch (SecurityException se) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(responseFactory.success(null, "Forbidden", HttpStatus.FORBIDDEN));
        } catch (IllegalArgumentException ia) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(responseFactory.success(null, "Not found", HttpStatus.NOT_FOUND));
        } catch (IllegalStateException is) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(responseFactory.success(null, "Invalid registration state", HttpStatus.CONFLICT));
        } catch (StripeException se) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(responseFactory.success(null, "Payment provider error", HttpStatus.BAD_GATEWAY));
        } catch (RuntimeException re) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(responseFactory.success(null, "Payment unavailable", HttpStatus.SERVICE_UNAVAILABLE));
        }
    }

    @PostMapping("/webhook")
    public ResponseEntity<String> webhook(@RequestHeader("Stripe-Signature") String sigHeader,
            @RequestBody String payload) {
        try {
            Event event = Webhook.constructEvent(payload, sigHeader, stripeWebhookSecret);
            log.info("Stripe webhook received event type={}", event.getType());
            if ("checkout.session.completed".equals(event.getType())) {
                EventDataObjectDeserializer dataObjectDeserializer = event.getDataObjectDeserializer();
                StripeObject stripeObject;
                if (dataObjectDeserializer.getObject().isPresent()) {
                    stripeObject = dataObjectDeserializer.getObject().get();
                } else {
                    log.warn(
                            "checkout.session.completed event did not deserialize into a Stripe Session; using unsafe fallback");
                    stripeObject = dataObjectDeserializer.deserializeUnsafe();
                }

                if (stripeObject instanceof Session) {
                    Session session = (Session) stripeObject;
                    String sessionId = session.getId();
                    log.info("Extracted Session ID: {}", sessionId);
                    log.info("About to call finalizePaymentForSession for providerRef={}", sessionId);
                    paymentService.finalizePaymentForSession(sessionId);
                } else {
                    log.warn("Payload was not a Stripe Checkout Session. Type: {}",
                            stripeObject != null ? stripeObject.getClass().getName() : "null");
                }
            } else {
                log.warn("Ignoring Stripe webhook event type={}", event.getType());
            }
            return ResponseEntity.ok("Webhook processed successfully");
        } catch (SignatureVerificationException sve) {
            log.warn("Stripe webhook signature verification failed", sve);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("invalid signature");
        } catch (Exception ex) {
            log.error("Stripe webhook processing failed", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("error");
        }
    }
}
