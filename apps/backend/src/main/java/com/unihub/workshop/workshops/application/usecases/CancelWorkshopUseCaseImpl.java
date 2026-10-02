package com.unihub.workshop.workshops.application.usecases;

import com.unihub.workshop.shared.events.WorkshopCanceledEvent;
import com.unihub.workshop.shared.exception.ConflictException;
import com.unihub.workshop.shared.exception.ForbiddenException;
import com.unihub.workshop.shared.exception.NotFoundException;
import com.unihub.workshop.shared.exception.ValidationException;
import com.unihub.workshop.workshops.application.ports.in.CancelWorkshopCommand;
import com.unihub.workshop.workshops.application.ports.in.CancelWorkshopUseCase;
import com.unihub.workshop.workshops.application.ports.out.WorkshopRepository;
import com.unihub.workshop.workshops.domain.model.Workshop;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CancelWorkshopUseCaseImpl implements CancelWorkshopUseCase {

    private final WorkshopRepository workshopRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public Workshop execute(CancelWorkshopCommand command) {
        if (command.version() == null) {
            throw new ValidationException("version", "must not be null");
        }

        validateProhibitedFields(command);

        String normalizedReason = null;
        if (command.reason() != null) {
            String trimmed = command.reason().trim();
            if (!trimmed.isEmpty()) {
                if (trimmed.length() > 1000) {
                    throw new ValidationException(
                        "reason",
                        "Must not exceed 1000 characters after trimming"
                    );
                }
                normalizedReason = trimmed;
            }
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
                "You do not have permission to cancel workshops for another organization."
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

        Workshop canceled = existing.cancel(command.callerId());

        Workshop saved = workshopRepository.save(canceled);

        eventPublisher.publishEvent(
            new WorkshopCanceledEvent(
                saved.id(),
                saved.organizationId(),
                saved.title(),
                saved.slug(),
                saved.startsAt(),
                saved.endsAt(),
                saved.canceledAt(),
                normalizedReason
            )
        );

        return saved;
    }

    private void validateProhibitedFields(CancelWorkshopCommand command) {
        List<String> prohibited = new ArrayList<>();
        if (command.title() != null) {
            prohibited.add(
                "title: Field is prohibited on cancel. Only version and reason are accepted."
            );
        }
        if (command.description() != null) {
            prohibited.add(
                "description: Field is prohibited on cancel. Only version and reason are accepted."
            );
        }
        if (command.coverImageUrl() != null) {
            prohibited.add(
                "coverImageUrl: Field is prohibited on cancel. Only version and reason are accepted."
            );
        }
        if (command.location() != null) {
            prohibited.add(
                "location: Field is prohibited on cancel. Only version and reason are accepted."
            );
        }
        if (command.timezone() != null) {
            prohibited.add(
                "timezone: Field is prohibited on cancel. Only version and reason are accepted."
            );
        }
        if (command.startsAt() != null) {
            prohibited.add(
                "startsAt: Field is prohibited on cancel. Only version and reason are accepted."
            );
        }
        if (command.endsAt() != null) {
            prohibited.add(
                "endsAt: Field is prohibited on cancel. Only version and reason are accepted."
            );
        }
        if (command.capacity() != null) {
            prohibited.add(
                "capacity: Field is prohibited on cancel. Only version and reason are accepted."
            );
        }
        if (command.price() != null) {
            prohibited.add(
                "price: Field is prohibited on cancel. Only version and reason are accepted."
            );
        }
        if (command.currency() != null) {
            prohibited.add(
                "currency: Field is prohibited on cancel. Only version and reason are accepted."
            );
        }
        if (command.speakers() != null) {
            prohibited.add(
                "speakers: Field is prohibited on cancel. Only version and reason are accepted."
            );
        }
        if (command.status() != null) {
            prohibited.add(
                "status: Field is prohibited on cancel. Only version and reason are accepted."
            );
        }
        if (command.requestedOrgId() != null) {
            prohibited.add(
                "organizationId: Field is prohibited on cancel. Only version and reason are accepted."
            );
        }

        if (!prohibited.isEmpty()) {
            String primaryField = prohibited.get(0).split(":")[0].trim();
            throw new ValidationException(
                "Field '" +
                    primaryField +
                    "' cannot be modified via the cancel endpoint.",
                prohibited
            );
        }
    }
}
