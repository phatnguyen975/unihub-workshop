package com.unihub.workshop.workshops.application.usecases;

import com.unihub.workshop.shared.events.WorkshopUpdatedEvent;
import com.unihub.workshop.shared.exception.ConflictException;
import com.unihub.workshop.shared.exception.ForbiddenException;
import com.unihub.workshop.shared.exception.NotFoundException;
import com.unihub.workshop.shared.exception.ValidationException;
import com.unihub.workshop.shared.storage.MediaStoragePort;
import com.unihub.workshop.workshops.application.ports.in.UpdateWorkshopCommand;
import com.unihub.workshop.workshops.application.ports.in.UpdateWorkshopUseCase;
import com.unihub.workshop.workshops.application.ports.out.SlugGeneratorPort;
import com.unihub.workshop.workshops.application.ports.out.WorkshopRegistrationCountPort;
import com.unihub.workshop.workshops.application.ports.out.WorkshopRepository;
import com.unihub.workshop.workshops.domain.model.Workshop;
import com.unihub.workshop.workshops.domain.model.WorkshopStatus;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UpdateWorkshopUseCaseImpl implements UpdateWorkshopUseCase {

    private final WorkshopRepository workshopRepository;
    private final WorkshopRegistrationCountPort registrationCountPort;
    private final SlugGeneratorPort slugGeneratorPort;
    private final MediaStoragePort mediaStoragePort;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public Workshop execute(UpdateWorkshopCommand command) {
        if (command.version() == null) {
            throw new ValidationException(
                "version",
                "version: must not be null"
            );
        }

        // Validate prohibited fields
        validateProhibitedFields(command);

        Workshop existing = workshopRepository
            .findById(command.workshopId())
            .orElseThrow(() ->
                new NotFoundException(
                    "Workshop not found with id: " + command.workshopId()
                )
            );

        if (!existing.organizationId().equals(command.organizationId())) {
            throw new ForbiddenException(
                "You do not have permission to update workshops for another organization."
            );
        }

        if (existing.status() != WorkshopStatus.PUBLISHED) {
            if (existing.status() == WorkshopStatus.DRAFT) {
                throw new ValidationException(
                    "status",
                    "Workshop is in DRAFT status. Please use POST /api/v1/admin/workshops/drafts to update drafts."
                );
            }
            throw new ValidationException(
                "status",
                "Workshops in terminal status (" +
                    existing.status() +
                    ") cannot be updated."
            );
        }

        if (!existing.version().equals(command.version())) {
            throw new ConflictException(
                String.format(
                    "Workshop version conflict: current version is %d, but request provided %d.",
                    existing.version(),
                    command.version()
                )
            );
        }

        // Validate capacity floor against active registrations
        if (command.capacity() != null) {
            long activeRegistrations =
                registrationCountPort.countActiveRegistrations(existing.id());
            if (command.capacity() < activeRegistrations) {
                throw new ValidationException(
                    "capacity",
                    "Capacity cannot be reduced below current active registration count: " +
                        activeRegistrations
                );
            }
        }

        // Synchronize slug if title is updated and different
        String slug = null;
        if (command.title() != null && !command.title().isBlank()) {
            slug = command.title().equals(existing.title())
                ? existing.slug()
                : slugGeneratorPort.generateSlug(command.title());
        }

        Workshop updated = existing.updatePublished(
            command.title(),
            slug,
            command.description(),
            command.coverImageUrl(),
            command.capacity(),
            command.callerId()
        );

        Workshop saved = workshopRepository.save(updated);

        // Media cleanup if cover image was altered
        cleanupOldCoverImage(existing.coverImageUrl(), command.coverImageUrl());

        // Emit outbox event for search synchronization
        eventPublisher.publishEvent(
            new WorkshopUpdatedEvent(
                saved.id(),
                saved.organizationId(),
                saved.title(),
                saved.slug(),
                saved.description(),
                saved.coverImageUrl(),
                saved.location(),
                saved.timezone(),
                saved.startsAt(),
                saved.endsAt(),
                saved.capacity(),
                saved.price(),
                saved.currency(),
                saved.publishedAt(),
                saved.updatedAt()
            )
        );

        return saved;
    }

    private void validateProhibitedFields(UpdateWorkshopCommand command) {
        List<String> prohibited = new ArrayList<>();
        if (command.price() != null) {
            prohibited.add("price is immutable on published workshops");
        }
        if (command.currency() != null) {
            prohibited.add("currency is immutable on published workshops");
        }
        if (command.startsAt() != null) {
            prohibited.add(
                "startsAt use reschedule endpoint to alter schedule"
            );
        }
        if (command.endsAt() != null) {
            prohibited.add("endsAt use reschedule endpoint to alter schedule");
        }
        if (command.status() != null) {
            prohibited.add("status transitions must use dedicated endpoints");
        }
        if (command.requestedOrgId() != null) {
            prohibited.add("organizationId is immutable");
        }
        if (command.speakers() != null) {
            prohibited.add("speakers cannot be altered on published workshops");
        }

        if (!prohibited.isEmpty()) {
            String primaryField = prohibited.get(0).split(":")[0];
            throw new ValidationException(
                "Prohibited field modification: " +
                    primaryField +
                    " cannot be modified via update endpoint.",
                prohibited
            );
        }
    }

    private void cleanupOldCoverImage(String oldCoverUrl, String newCoverUrl) {
        if (
            newCoverUrl != null &&
            oldCoverUrl != null &&
            !oldCoverUrl.isBlank() &&
            !oldCoverUrl.equals(newCoverUrl)
        ) {
            try {
                mediaStoragePort.deleteByUrl(oldCoverUrl);
            } catch (Exception e) {
                // best-effort cleanup, do not fail transaction
            }
        }
    }
}
