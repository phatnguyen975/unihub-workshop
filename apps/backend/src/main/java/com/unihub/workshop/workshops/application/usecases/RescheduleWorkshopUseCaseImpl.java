package com.unihub.workshop.workshops.application.usecases;

import com.unihub.workshop.shared.events.WorkshopRescheduledEvent;
import com.unihub.workshop.shared.exception.ConflictException;
import com.unihub.workshop.shared.exception.ForbiddenException;
import com.unihub.workshop.shared.exception.NotFoundException;
import com.unihub.workshop.shared.exception.ValidationException;
import com.unihub.workshop.workshops.application.ports.in.RescheduleWorkshopCommand;
import com.unihub.workshop.workshops.application.ports.in.RescheduleWorkshopUseCase;
import com.unihub.workshop.workshops.application.ports.out.WorkshopRepository;
import com.unihub.workshop.workshops.domain.model.Speaker;
import com.unihub.workshop.workshops.domain.model.Workshop;
import com.unihub.workshop.workshops.domain.model.WorkshopStatus;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RescheduleWorkshopUseCaseImpl
    implements RescheduleWorkshopUseCase
{

    private final WorkshopRepository workshopRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public Workshop execute(RescheduleWorkshopCommand command) {
        if (command.version() == null) {
            throw new ValidationException("version", "must not be null");
        }

        // Validate prohibited fields
        validateProhibitedFields(command);

        // Basic chronological and future schedule validations
        if (command.startsAt() == null) {
            throw new ValidationException("startsAt", "must not be null");
        }
        if (command.endsAt() == null) {
            throw new ValidationException("endsAt", "must not be null");
        }
        if (!command.endsAt().isAfter(command.startsAt())) {
            throw new ValidationException(
                "endsAt",
                "endsAt must be strictly after startsAt"
            );
        }

        Instant now = Instant.now();
        if (!command.startsAt().isAfter(now)) {
            throw new ValidationException(
                "startsAt",
                "Workshop start time must be in the future"
            );
        }

        Workshop existing = workshopRepository
            .findById(command.workshopId())
            .orElseThrow(() ->
                new NotFoundException(
                    "Workshop not found with id: " + command.workshopId()
                )
            );

        if (!existing.organizationId().equals(command.organizationId())) {
            throw new ForbiddenException(
                "You do not have permission to reschedule workshops for another organization."
            );
        }

        if (existing.status() != WorkshopStatus.PUBLISHED) {
            if (existing.status() == WorkshopStatus.DRAFT) {
                throw new ValidationException(
                    "status",
                    "Workshop is in DRAFT status. Please use the draft endpoint to modify the schedule."
                );
            }
            throw new ValidationException(
                "status",
                "Workshops in terminal status (" +
                    existing.status() +
                    ") cannot be rescheduled."
            );
        }

        if (!existing.version().equals(command.version())) {
            throw new ConflictException(
                String.format(
                    "Workshop version conflict: expected version %d but found version %d.",
                    command.version(),
                    existing.version()
                )
            );
        }

        // Speaker schedule collision check across active workshops in new time window
        if (existing.speakers() != null && !existing.speakers().isEmpty()) {
            List<String> speakerEmails = existing
                .speakers()
                .stream()
                .map(Speaker::email)
                .filter(email -> email != null && !email.isBlank())
                .toList();

            if (!speakerEmails.isEmpty()) {
                List<String> conflictingEmails =
                    workshopRepository.findConflictingSpeakerEmails(
                        speakerEmails,
                        command.startsAt(),
                        command.endsAt(),
                        existing.id()
                    );

                if (!conflictingEmails.isEmpty()) {
                    String conflictList = String.join(", ", conflictingEmails);
                    throw new ValidationException(
                        "Speaker schedule collision detected for speaker email: " +
                            conflictList +
                            " in the new time window.",
                        conflictingEmails
                            .stream()
                            .map(
                                email ->
                                    "speakers: Speaker '" +
                                    email +
                                    "' has an overlapping schedule with another active workshop in the new time window"
                            )
                            .toList()
                    );
                }
            }
        }

        // Campus-wide location collision check across active workshops in new time window
        if (existing.location() != null && !existing.location().isBlank()) {
            Optional<UUID> collidingWorkshopId =
                workshopRepository.findCollidingWorkshopAtLocation(
                    existing.location(),
                    command.startsAt(),
                    command.endsAt(),
                    existing.id()
                );

            if (collidingWorkshopId.isPresent()) {
                throw new ValidationException(
                    "location",
                    "Location collision detected: another active workshop is scheduled at the same location during the new time window."
                );
            }
        }

        Instant previousStartsAt = existing.startsAt();
        Instant previousEndsAt = existing.endsAt();

        Workshop rescheduled = existing.reschedule(
            command.startsAt(),
            command.endsAt(),
            command.callerId()
        );

        Workshop saved = workshopRepository.save(rescheduled);

        eventPublisher.publishEvent(
            new WorkshopRescheduledEvent(
                saved.id(),
                saved.organizationId(),
                saved.title(),
                saved.slug(),
                saved.location(),
                saved.timezone(),
                previousStartsAt,
                previousEndsAt,
                saved.startsAt(),
                saved.endsAt(),
                saved.updatedAt()
            )
        );

        return saved;
    }

    private void validateProhibitedFields(RescheduleWorkshopCommand command) {
        List<String> prohibited = new ArrayList<>();
        if (command.title() != null) {
            prohibited.add("title: Field is prohibited on reschedule");
        }
        if (command.description() != null) {
            prohibited.add("description: Field is prohibited on reschedule");
        }
        if (command.coverImageUrl() != null) {
            prohibited.add("coverImageUrl: Field is prohibited on reschedule");
        }
        if (command.location() != null) {
            prohibited.add(
                "location: Field is prohibited on reschedule. Location cannot be modified via the reschedule endpoint."
            );
        }
        if (command.capacity() != null) {
            prohibited.add("capacity: Field is prohibited on reschedule");
        }
        if (command.price() != null) {
            prohibited.add("price: Field is prohibited on reschedule");
        }
        if (command.currency() != null) {
            prohibited.add("currency: Field is prohibited on reschedule");
        }
        if (command.speakers() != null) {
            prohibited.add("speakers: Field is prohibited on reschedule");
        }
        if (command.status() != null) {
            prohibited.add(
                "status: Status transitions must use dedicated endpoints"
            );
        }
        if (command.requestedOrgId() != null) {
            prohibited.add("organizationId: Organization is immutable");
        }

        if (!prohibited.isEmpty()) {
            String primaryField = prohibited.get(0).split(":")[0].trim();
            throw new ValidationException(
                "Field '" +
                    primaryField +
                    "' cannot be modified via the reschedule endpoint.",
                prohibited
            );
        }
    }
}
