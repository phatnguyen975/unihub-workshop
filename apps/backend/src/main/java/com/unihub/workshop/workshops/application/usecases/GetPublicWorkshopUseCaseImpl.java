package com.unihub.workshop.workshops.application.usecases;

import com.unihub.workshop.shared.exception.NotFoundException;
import com.unihub.workshop.workshops.application.ports.in.GetPublicWorkshopQuery;
import com.unihub.workshop.workshops.application.ports.in.GetPublicWorkshopUseCase;
import com.unihub.workshop.workshops.application.ports.out.WorkshopAvailabilityPort;
import com.unihub.workshop.workshops.application.ports.out.WorkshopRepository;
import com.unihub.workshop.workshops.domain.model.PublicWorkshopDetail;
import com.unihub.workshop.workshops.domain.model.Workshop;
import com.unihub.workshop.workshops.domain.model.WorkshopStatus;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class GetPublicWorkshopUseCaseImpl implements GetPublicWorkshopUseCase {

    private final WorkshopRepository workshopRepository;
    private final WorkshopAvailabilityPort availabilityPort;

    @Override
    @Transactional(readOnly = true)
    public PublicWorkshopDetail execute(GetPublicWorkshopQuery query) {
        String identifier = query.identifier();
        Optional<Workshop> workshopOpt = tryParseUuid(identifier)
            .map(workshopRepository::findById)
            .orElseGet(() -> workshopRepository.findBySlug(identifier));

        Workshop workshop = workshopOpt.orElseThrow(() ->
            new NotFoundException("Workshop not found.")
        );

        if (workshop.status() != WorkshopStatus.PUBLISHED) {
            // Anti-enumeration: return identical 404 for non-published workshops
            throw new NotFoundException("Workshop not found.");
        }

        int capacity = workshop.capacity() != null ? workshop.capacity() : 0;
        int availableSeats = availabilityPort.resolveAvailableSeats(
            workshop.id(),
            capacity
        );

        return PublicWorkshopDetail.from(workshop, availableSeats);
    }

    private Optional<UUID> tryParseUuid(String value) {
        if (value == null || value.length() != 36) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(value));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
