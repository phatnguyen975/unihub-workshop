package com.unihub.workshop.registrations.service;

import com.unihub.workshop.registrations.domain.Registration;
import com.unihub.workshop.registrations.repository.RegistrationRepository;
import com.unihub.workshop.workshops.domain.model.Workshop;
import com.unihub.workshop.workshops.domain.repository.WorkshopRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Slf4j
public class PaymentTimeoutSweeperProcessor {

    private final RegistrationRepository registrationRepository;
    private final WorkshopRepository workshopRepository;

    public PaymentTimeoutSweeperProcessor(RegistrationRepository registrationRepository,
            WorkshopRepository workshopRepository) {
        this.registrationRepository = registrationRepository;
        this.workshopRepository = workshopRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processRegistration(UUID registrationId) {
        log.info("Entering payment timeout compensation for registration ID: {}", registrationId);

        Registration registration = registrationRepository.findByIdForUpdate(registrationId)
                .orElseThrow(() -> new IllegalStateException("Registration missing for expired registration " + registrationId));

        if (!"PENDING".equals(registration.getStatus()) && !"CONFIRMED".equals(registration.getStatus())) {
            log.info("Skipping registration ID: {} because current status is {}", registrationId, registration.getStatus());
            return;
        }

        Workshop workshop = workshopRepository.findByIdForUpdate(registration.getWorkshop().getId())
                .orElseThrow(() -> new IllegalStateException(
                        "Workshop missing for expired registration " + registration.getId()));

        registration.setStatus("CANCELED");
        workshop.setCapacityRemaining(Math.min(workshop.getCapacityRemaining() + 1, workshop.getCapacityTotal()));

        log.info("Attempting to save updated entities to DB.");
        registrationRepository.save(registration);
        workshopRepository.save(workshop);
        log.info("Successfully canceled registration ID: {} and restored capacity.", registrationId);
    }
}
