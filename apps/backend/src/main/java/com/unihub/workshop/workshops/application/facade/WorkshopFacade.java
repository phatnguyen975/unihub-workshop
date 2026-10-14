package com.unihub.workshop.workshops.application.facade;

import com.unihub.workshop.workshops.api.WorkshopApi;
import com.unihub.workshop.workshops.api.dto.WorkshopRegistrationDetailsDto;
import com.unihub.workshop.workshops.application.ports.out.WorkshopRepository;
import com.unihub.workshop.workshops.domain.model.Workshop;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementation of the public {@link WorkshopApi} facade.
 * Coordinates internal Workshop ports/use cases to satisfy cross-module requests.
 */
@Component
@RequiredArgsConstructor
public class WorkshopFacade implements WorkshopApi {

    private final WorkshopRepository workshopRepository;

    @Override
    @Transactional(readOnly = true)
    public Optional<WorkshopRegistrationDetailsDto> getWorkshopForRegistration(
        UUID workshopId
    ) {
        if (workshopId == null) {
            return Optional.empty();
        }
        return workshopRepository.findById(workshopId).map(this::toDetailsDto);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<
        UUID,
        WorkshopRegistrationDetailsDto
    > getWorkshopsForRegistrations(Collection<UUID> workshopIds) {
        if (workshopIds == null || workshopIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return workshopRepository
            .findAllById(workshopIds)
            .stream()
            .map(this::toDetailsDto)
            .collect(
                Collectors.toMap(
                    WorkshopRegistrationDetailsDto::id,
                    Function.identity()
                )
            );
    }

    private WorkshopRegistrationDetailsDto toDetailsDto(Workshop workshop) {
        return new WorkshopRegistrationDetailsDto(
            workshop.id(),
            workshop.organizationId(),
            workshop.title(),
            workshop.slug(),
            workshop.status() != null ? workshop.status().name() : null,
            workshop.startsAt(),
            workshop.endsAt(),
            workshop.price(),
            workshop.capacity(),
            workshop.location()
        );
    }
}
