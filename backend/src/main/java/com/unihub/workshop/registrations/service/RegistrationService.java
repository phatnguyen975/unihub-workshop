package com.unihub.workshop.registrations.service;

import com.unihub.workshop.registrations.domain.Registration;
import com.unihub.workshop.registrations.repository.RegistrationRepository;
import com.unihub.workshop.iam.infrastructure.security.JwtTokenProvider;
import com.unihub.workshop.workshops.domain.model.Workshop;
import com.unihub.workshop.workshops.domain.repository.WorkshopRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.unihub.workshop.registrations.domain.events.RegistrationConfirmedEvent;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class RegistrationService {

    private final RegistrationRepository registrationRepository;
    private final WorkshopRepository workshopRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final ApplicationEventPublisher eventPublisher;

    public RegistrationService(RegistrationRepository registrationRepository,
            WorkshopRepository workshopRepository,
            JwtTokenProvider jwtTokenProvider,
            ApplicationEventPublisher eventPublisher) {
        this.registrationRepository = registrationRepository;
        this.workshopRepository = workshopRepository;
        this.jwtTokenProvider = jwtTokenProvider;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public Registration reserveSeat(UUID workshopId, UUID userId, String idempotencyKey) {
        // Load workshop with pessimistic lock
        Workshop workshop = workshopRepository.findByIdForUpdate(workshopId)
                .orElseThrow(() -> new IllegalArgumentException("Workshop not found"));

        // Time conflict check
        OffsetDateTime start = workshop.getStartTime();
        OffsetDateTime end = workshop.getEndTime();
        List<Registration> overlaps = registrationRepository.findOverlappingByUserIdAndStatuses(
                userId,
                List.of("PENDING", "CONFIRMED"),
                start,
                end);
        if (!overlaps.isEmpty()) {
            throw new RuntimeException("SCHEDULE_OVERLAP");
        }

        if (workshop.getCapacityRemaining() <= 0) {
            throw new RuntimeException("SOLD_OUT");
        }

        workshop.setCapacityRemaining(workshop.getCapacityRemaining() - 1);

        Registration registration = new Registration();
        registration.setUserId(userId);
        registration.setWorkshop(workshop);
        registration.setIdempotencyKey(idempotencyKey);

        BigDecimal price = workshop.getPriceAmount();
        if (price == null || price.compareTo(BigDecimal.ZERO) == 0) {
            registration.setStatus("CONFIRMED");
            registration.setTicketType("FREE");
            // Publish confirmation event after successful commit
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    eventPublisher.publishEvent(new RegistrationConfirmedEvent(registration.getId(),
                            registration.getUserId(), workshop.getId()));
                }
            });
        } else {
            registration.setStatus("PENDING");
            registration.setTicketType("PAID");
        }

        registrationRepository.save(registration);
        workshopRepository.save(workshop);

        return registration;
    }

    @Transactional
    public Registration cancelRegistration(UUID registrationId, UUID userId) {
        Registration registration = registrationRepository.findByIdForUpdate(registrationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Registration not found"));

        if (!registration.getUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not the owner of this registration");
        }

        if ("CANCELED".equals(registration.getStatus())) {
            return registration;
        }

        if (!"PENDING".equals(registration.getStatus()) && !"CONFIRMED".equals(registration.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Registration is not eligible for cancellation");
        }

        Workshop workshop = workshopRepository.findByIdForUpdate(registration.getWorkshop().getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Workshop not found"));

        OffsetDateTime now = OffsetDateTime.now();
        if (!now.isBefore(workshop.getStartTime())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot cancel after the event has started");
        }

        registration.setStatus("CANCELED");
        workshop.setCapacityRemaining(Math.min(workshop.getCapacityRemaining() + 1, workshop.getCapacityTotal()));

        registrationRepository.save(registration);
        workshopRepository.save(workshop);

        return registration;
    }

    @Transactional
    public String issueTicket(UUID registrationId, UUID userId) {
        Registration registration = registrationRepository.findById(registrationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Registration not found"));

        if (!registration.getUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not the owner of this registration");
        }

        if (!"CONFIRMED".equals(registration.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ticket only available for confirmed registrations");
        }

        Workshop workshop = registration.getWorkshop();
        return jwtTokenProvider.generateTicketToken(
                registration.getId(),
                workshop.getId(),
                userId);
    }
}
