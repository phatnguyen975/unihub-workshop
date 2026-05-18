package com.unihub.workshop.registrations.service;

import com.unihub.workshop.registrations.domain.Registration;
import com.unihub.workshop.registrations.repository.RegistrationRepository;
import com.unihub.workshop.workshops.domain.model.Workshop;
import com.unihub.workshop.workshops.domain.repository.WorkshopRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class PaymentTimeoutSweeperProcessor {

    private final RegistrationRepository registrationRepository;
    private final WorkshopRepository workshopRepository;

    public PaymentTimeoutSweeperProcessor(RegistrationRepository registrationRepository,
            WorkshopRepository workshopRepository) {
        this.registrationRepository = registrationRepository;
        this.workshopRepository = workshopRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean processNextExpiredRegistration() {
        List<Registration> candidates = registrationRepository.findNextExpiredPendingForUpdateSkipLocked();
        if (candidates.isEmpty()) {
            return false;
        }

        Registration registration = candidates.getFirst();
        if (!"PENDING".equals(registration.getStatus()) && !"CONFIRMED".equals(registration.getStatus())) {
            return true;
        }

        Workshop workshop = workshopRepository.findByIdForUpdate(registration.getWorkshop().getId())
                .orElseThrow(() -> new IllegalStateException(
                        "Workshop missing for expired registration " + registration.getId()));

        registration.setStatus("CANCELED");
        workshop.setCapacityRemaining(Math.min(workshop.getCapacityRemaining() + 1, workshop.getCapacityTotal()));

        registrationRepository.save(registration);
        workshopRepository.save(workshop);
        return true;
    }
}
