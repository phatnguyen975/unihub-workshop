package com.unihub.workshop.registrations.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class PaymentTimeoutSweeperJob {

    private final PaymentTimeoutSweeperProcessor processor;

    public PaymentTimeoutSweeperJob(PaymentTimeoutSweeperProcessor processor) {
        this.processor = processor;
    }

    @Scheduled(cron = "0 */5 * * * *")
    public void sweepExpiredPayments() {
        while (processor.processNextExpiredRegistration()) {
            // Process one record per transaction to keep compensation isolated
        }
    }
}
