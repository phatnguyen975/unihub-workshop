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
import com.unihub.workshop.workshops.domain.model.Speaker;
import com.unihub.workshop.workshops.domain.model.Workshop;
import com.unihub.workshop.workshops.domain.model.WorkshopStatus;
import java.math.BigDecimal;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Currency;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SaveDraftUseCaseImpl implements SaveDraftUseCase {

    private static final BigDecimal MAX_PRICE = new BigDecimal("99999999.99");
    private static final int MAX_CAPACITY = 1_000_000;
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"
    );

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

        List<Speaker> domainSpeakers = validateAndMapSpeakers(command);

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
                currency,
                domainSpeakers
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
            currency,
            domainSpeakers
        );
    }

    private List<Speaker> validateAndMapSpeakers(SaveDraftCommand command) {
        if (command.speakers() == null) {
            return null;
        }

        if (command.speakers().size() > 10) {
            throw new ValidationException(
                "speakers",
                "A workshop cannot have more than 10 speakers"
            );
        }

        if (command.speakers().isEmpty()) {
            return List.of();
        }

        if (command.startsAt() == null || command.endsAt() == null) {
            throw new ValidationException(
                "speakers",
                "startsAt and endsAt are mandatory when attaching speakers"
            );
        }

        Set<String> seenEmails = new HashSet<>();
        List<String> emailsToCheck = new ArrayList<>();
        List<Speaker> domainSpeakers = new ArrayList<>();

        for (int i = 0; i < command.speakers().size(); i++) {
            SaveDraftCommand.SpeakerCommand sc = command.speakers().get(i);
            if (sc == null) {
                throw new ValidationException(
                    "speakers",
                    "Speaker entry must not be null"
                );
            }

            String speakerName = cleanString(sc.name());
            if (speakerName == null) {
                throw new ValidationException(
                    "speakers",
                    "Speaker name is mandatory"
                );
            }
            if (speakerName.length() > 200) {
                throw new ValidationException(
                    "speakers",
                    "Speaker name must not exceed 200 characters"
                );
            }

            String speakerEmail = cleanString(sc.email());
            if (speakerEmail == null) {
                throw new ValidationException(
                    "speakers",
                    "Speaker email is mandatory"
                );
            }
            if (speakerEmail.length() > 255) {
                throw new ValidationException(
                    "speakers",
                    "Speaker email must not exceed 255 characters"
                );
            }
            if (!EMAIL_PATTERN.matcher(speakerEmail).matches()) {
                throw new ValidationException(
                    "speakers",
                    "Speaker email must be a valid email address"
                );
            }

            String normalizedEmail = speakerEmail.toLowerCase();
            if (!seenEmails.add(normalizedEmail)) {
                throw new ValidationException(
                    "speakers",
                    "Duplicate speaker email: " + normalizedEmail
                );
            }
            emailsToCheck.add(normalizedEmail);

            String bio = cleanString(sc.bio());
            if (bio != null && bio.length() > 2000) {
                throw new ValidationException(
                    "speakers",
                    "Speaker bio must not exceed 2000 characters"
                );
            }

            String avatarUrl = cleanString(sc.avatarUrl());
            if (avatarUrl != null) {
                if (avatarUrl.length() > 2048) {
                    throw new ValidationException(
                        "speakers",
                        "Speaker avatarUrl must not exceed 2048 characters"
                    );
                }
                if (
                    !avatarUrl.startsWith("http://") &&
                    !avatarUrl.startsWith("https://")
                ) {
                    throw new ValidationException(
                        "speakers",
                        "Speaker avatarUrl must be a valid HTTP or HTTPS URL"
                    );
                }
            }

            String linkedinUrl = cleanString(sc.linkedinUrl());
            if (linkedinUrl != null) {
                if (linkedinUrl.length() > 2048) {
                    throw new ValidationException(
                        "speakers",
                        "Speaker linkedinUrl must not exceed 2048 characters"
                    );
                }
                if (
                    !linkedinUrl.startsWith("http://") &&
                    !linkedinUrl.startsWith("https://")
                ) {
                    throw new ValidationException(
                        "speakers",
                        "Speaker linkedinUrl must be a valid HTTP or HTTPS URL"
                    );
                }
            }

            int sortOrder = sc.sortOrder() != null ? sc.sortOrder() : i;
            domainSpeakers.add(
                Speaker.create(
                    command.id(),
                    speakerName,
                    normalizedEmail,
                    bio,
                    avatarUrl,
                    linkedinUrl,
                    sortOrder,
                    command.callerId()
                )
            );
        }

        List<String> conflicts =
            workshopRepository.findConflictingSpeakerEmails(
                emailsToCheck,
                command.startsAt(),
                command.endsAt(),
                command.id()
            );
        if (!conflicts.isEmpty()) {
            throw new ValidationException(
                "speakers",
                "Speaker schedule conflict detected for email(s): " +
                    String.join(", ", conflicts)
            );
        }

        return domainSpeakers;
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
        String currency,
        List<Speaker> domainSpeakers
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
                    "Workshop not found with id: " + command.id()
                )
            );

        if (!existing.organizationId().equals(command.organizationId())) {
            throw new ForbiddenException(
                "You do not have access to workshops in other organizations"
            );
        }

        if (existing.status() != WorkshopStatus.DRAFT) {
            throw new ConflictException(
                "Only workshops in DRAFT status can be updated as a draft. Current status: " +
                    existing.status()
            );
        }

        if (!existing.version().equals(command.version())) {
            throw new ConflictException(
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
            command.callerId(),
            domainSpeakers
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
        String currency,
        List<Speaker> domainSpeakers
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
            command.callerId(),
            domainSpeakers
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
