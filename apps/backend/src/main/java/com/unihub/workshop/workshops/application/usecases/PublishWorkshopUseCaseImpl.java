package com.unihub.workshop.workshops.application.usecases;

import com.unihub.workshop.shared.events.WorkshopPublishedEvent;
import com.unihub.workshop.shared.exception.ConflictException;
import com.unihub.workshop.shared.exception.ForbiddenException;
import com.unihub.workshop.shared.exception.NotFoundException;
import com.unihub.workshop.shared.exception.ValidationException;
import com.unihub.workshop.workshops.application.ports.in.PublishWorkshopCommand;
import com.unihub.workshop.workshops.application.ports.in.PublishWorkshopUseCase;
import com.unihub.workshop.workshops.application.ports.out.WorkshopRepository;
import com.unihub.workshop.workshops.domain.model.Speaker;
import com.unihub.workshop.workshops.domain.model.Workshop;
import com.unihub.workshop.workshops.domain.model.WorkshopStatus;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PublishWorkshopUseCaseImpl implements PublishWorkshopUseCase {

    private final WorkshopRepository workshopRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public Workshop execute(PublishWorkshopCommand command) {
        if (command.version() == null) {
            throw new ValidationException(
                "version",
                "version: must not be null"
            );
        }

        Workshop workshop = workshopRepository
            .findById(command.workshopId())
            .orElseThrow(() ->
                new NotFoundException(
                    "Workshop not found with id: " + command.workshopId()
                )
            );

        if (!workshop.organizationId().equals(command.organizationId())) {
            throw new ForbiddenException(
                "You do not have permission to publish workshops for another organization."
            );
        }

        if (workshop.status() != WorkshopStatus.DRAFT) {
            throw new ValidationException(
                "status",
                "Only workshops in DRAFT status can be published. Current status: " +
                    workshop.status()
            );
        }

        if (!workshop.version().equals(command.version())) {
            throw new ConflictException(
                String.format(
                    "Workshop version conflict: expected version %d but found version %d.",
                    command.version(),
                    workshop.version()
                )
            );
        }

        // Speaker schedule collision check across active workshops
        if (workshop.speakers() != null && !workshop.speakers().isEmpty()) {
            List<String> speakerEmails = workshop
                .speakers()
                .stream()
                .map(Speaker::email)
                .filter(email -> email != null && !email.isBlank())
                .toList();

            if (!speakerEmails.isEmpty()) {
                List<String> conflictingEmails =
                    workshopRepository.findConflictingSpeakerEmails(
                        speakerEmails,
                        workshop.startsAt(),
                        workshop.endsAt(),
                        workshop.id()
                    );

                if (!conflictingEmails.isEmpty()) {
                    String conflictList = String.join(", ", conflictingEmails);
                    throw new ValidationException(
                        "Speaker schedule collision detected for speaker email: " +
                            conflictList +
                            " during overlapping workshop.",
                        conflictingEmails
                            .stream()
                            .map(
                                email ->
                                    "speakers: Speaker '" +
                                    email +
                                    "' has an overlapping schedule with another active workshop"
                            )
                            .toList()
                    );
                }
            }
        }

        Workshop published = workshop.publish(command.callerId());
        Workshop saved = workshopRepository.save(published);

        eventPublisher.publishEvent(
            new WorkshopPublishedEvent(
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
                saved.publishedAt()
            )
        );

        return saved;
    }
}
