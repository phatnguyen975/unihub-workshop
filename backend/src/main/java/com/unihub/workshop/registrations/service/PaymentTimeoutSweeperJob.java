package com.unihub.workshop.registrations.service;

import com.unihub.workshop.registrations.repository.RegistrationRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class PaymentTimeoutSweeperJob {

    private final PaymentTimeoutSweeperProcessor processor;
    private final RegistrationRepository registrationRepository;

    public PaymentTimeoutSweeperJob(PaymentTimeoutSweeperProcessor processor,
            RegistrationRepository registrationRepository) {
        this.processor = processor;
        this.registrationRepository = registrationRepository;
    }

    @Scheduled(cron = "0 */5 * * * *")
    public void sweepExpiredPayments() {
        log.info("Starting Payment Timeout Sweeper execution...");
        var expiredRegistrations = registrationRepository.findNextExpiredPendingForUpdateSkipLocked();
        log.info("Found {} expired pending registrations to sweep.", expiredRegistrations.size());

        for (var registration : expiredRegistrations) {
            try {
                log.info("Sweeping registration ID: {}", registration.getId());
                processor.processRegistration(registration.getId());
                log.info("Successfully canceled registration ID: {} and restored capacity.", registration.getId());
            } catch (Exception e) {
                log.error("Failed to sweep registration ID: {}", registration.getId(), e);
            }
        }

        log.info("Payment Timeout Sweeper finished execution.");
    }
}
