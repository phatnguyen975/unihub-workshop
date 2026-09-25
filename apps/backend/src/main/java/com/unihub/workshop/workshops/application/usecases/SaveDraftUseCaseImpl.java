package com.unihub.workshop.workshops.application.usecases;

import com.unihub.workshop.shared.exception.BadRequestException;
import com.unihub.workshop.shared.exception.ConflictException;
import com.unihub.workshop.shared.exception.ForbiddenException;
import com.unihub.workshop.shared.exception.NotFoundException;
import com.unihub.workshop.shared.exception.ValidationException;
import com.unihub.workshop.workshops.application.ports.in.SaveDraftCommand;
import com.unihub.workshop.workshops.application.ports.in.SaveDraftUseCase;
import com.unihub.workshop.workshops.application.ports.out.SlugGeneratorPort;
import com.unihub.workshop.workshops.application.ports.out.WorkshopRepository;
import com.unihub.workshop.workshops.domain.model.Workshop;
import com.unihub.workshop.workshops.domain.model.WorkshopStatus;
import java.math.BigDecimal;
import java.time.ZoneId;
import java.util.Currency;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SaveDraftUseCaseImpl implements SaveDraftUseCase {

    private static final BigDecimal MAX_PRICE = new BigDecimal("99999999.99");
    private static final int MAX_CAPACITY = 1_000_000;

    private final WorkshopRepository workshopRepository;
    private final SlugGeneratorPort slugGeneratorPort;

    @Override
    @Transactional
    public Workshop execute(SaveDraftCommand command) {
        if (command == null) {
            throw new BadRequestException("Command must not be null");
        }
        if (command.organizationId() == null) {
            throw new BadRequestException("organizationId must not be null");
        }
        if (command.callerId() == null) {
            throw new BadRequestException("callerId must not be null");
        }

        String title = cleanString(command.title());
        if (title == null) {
            throw new ValidationException("title", "Title must not be blank");
        }
        if (title.length() > 255) {
            throw new ValidationException(
                "title",
                "Title must not exceed 255 characters"
            );
        }

        String description = cleanString(command.description());
        if (description != null && description.length() > 5000) {
            throw new ValidationException(
                "description",
                "Description must not exceed 5000 characters"
            );
        }

        String coverImageUrl = cleanString(command.coverImageUrl());
        if (coverImageUrl != null) {
            if (coverImageUrl.length() > 2048) {
                throw new ValidationException(
                    "coverImageUrl",
                    "coverImageUrl must not exceed 2048 characters"
                );
            }
            if (
                !coverImageUrl.startsWith("http://") &&
                !coverImageUrl.startsWith("https://")
            ) {
                throw new ValidationException(
                    "coverImageUrl",
                    "coverImageUrl must be a valid HTTP or HTTPS URL"
                );
            }
        }

        String location = cleanString(command.location());
        if (location != null && location.length() > 255) {
            throw new ValidationException(
                "location",
                "Location must not exceed 255 characters"
            );
        }

        String timezone = cleanString(command.timezone());
        if (timezone != null) {
            if (timezone.length() > 64) {
                throw new ValidationException(
                    "timezone",
                    "Timezone must not exceed 64 characters"
                );
            }
            try {
                ZoneId.of(timezone);
            } catch (Exception e) {
                throw new ValidationException(
                    "timezone",
                    "Invalid IANA timezone identifier: " + timezone
                );
            }
        }

        Integer capacity = command.capacity();
        if (capacity != null) {
            if (capacity < 1) {
                throw new ValidationException(
                    "capacity",
                    "Capacity must be at least 1"
                );
            }
            if (capacity > MAX_CAPACITY) {
                throw new ValidationException(
                    "capacity",
                    "Capacity must not exceed 1,000,000"
                );
            }
        }

        BigDecimal price = command.price();
        if (price != null) {
            if (price.compareTo(BigDecimal.ZERO) < 0) {
                throw new ValidationException(
                    "price",
                    "Price must be non-negative"
                );
            }
            if (price.scale() > 2) {
                throw new ValidationException(
                    "price",
                    "Price must have at most 2 decimal places"
                );
            }
            if (price.compareTo(MAX_PRICE) > 0) {
                throw new ValidationException(
                    "price",
                    "Price must not exceed 99,999,999.99"
                );
            }
        }

        String currency = cleanString(command.currency());
        if (currency != null) {
            try {
                Currency.getInstance(currency.toUpperCase());
                currency = currency.toUpperCase();
            } catch (IllegalArgumentException e) {
                throw new ValidationException(
                    "currency",
                    "Invalid ISO 4217 currency code: " + currency
                );
            }
        }

        if (command.startsAt() != null && command.endsAt() != null) {
            if (!command.endsAt().isAfter(command.startsAt())) {
                throw new ValidationException(
                    "endsAt",
                    "endsAt must be strictly after startsAt"
                );
            }
        }

        if (command.id() == null) {
            return createDraft(
                command,
                title,
                description,
                coverImageUrl,
                location,
                timezone,
                capacity,
                price,
                currency
            );
        }

        return updateDraft(
            command,
            title,
            description,
            coverImageUrl,
            location,
            timezone,
            capacity,
            price,
            currency
        );
    }

    private Workshop updateDraft(
        SaveDraftCommand command,
        String title,
        String description,
        String coverImageUrl,
        String location,
        String timezone,
        Integer capacity,
        BigDecimal price,
        String currency
    ) {
        if (command.version() == null) {
            throw new ValidationException(
                "version",
                "Version is required when updating an existing draft"
            );
        }

        Workshop existing = workshopRepository
            .findById(command.id())
            .orElseThrow(() ->
                new NotFoundException(
                    "WORKSHOP_NOT_FOUND",
                    "Workshop not found with id: " + command.id()
                )
            );

        if (!existing.organizationId().equals(command.organizationId())) {
            throw new ForbiddenException(
                "FORBIDDEN",
                "You do not have access to workshops in other organizations"
            );
        }

        if (existing.status() != WorkshopStatus.DRAFT) {
            throw new ConflictException(
                "INVALID_WORKSHOP_STATUS",
                "Only workshops in DRAFT status can be updated as a draft. Current status: " +
                    existing.status()
            );
        }

        if (!existing.version().equals(command.version())) {
            throw new ConflictException(
                "VERSION_CONFLICT",
                String.format(
                    "Workshop version conflict: current version is %d, but request provided %d",
                    existing.version(),
                    command.version()
                )
            );
        }

        String slug = existing.title().equals(title)
            ? existing.slug()
            : slugGeneratorPort.generateSlug(title);

        Workshop updated = existing.updateDraft(
            title,
            slug,
            description,
            coverImageUrl,
            location,
            timezone,
            command.startsAt(),
            command.endsAt(),
            capacity,
            price,
            currency,
            command.callerId()
        );

        return workshopRepository.save(updated);
    }

    private Workshop createDraft(
        SaveDraftCommand command,
        String title,
        String description,
        String coverImageUrl,
        String location,
        String timezone,
        Integer capacity,
        BigDecimal price,
        String currency
    ) {
        String slug = slugGeneratorPort.generateSlug(title);

        Workshop draft = Workshop.createDraft(
            command.organizationId(),
            title,
            slug,
            description,
            coverImageUrl,
            location,
            timezone,
            command.startsAt(),
            command.endsAt(),
            capacity,
            price,
            currency,
            command.callerId()
        );

        return workshopRepository.save(draft);
    }

    private String cleanString(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
