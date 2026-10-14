package com.unihub.workshop.registrations.application.usecases;

import com.unihub.workshop.registrations.application.ports.in.GetMyRegistrationsQuery;
import com.unihub.workshop.registrations.application.ports.in.GetMyRegistrationsUseCase;
import com.unihub.workshop.registrations.application.ports.out.RegistrationRepository;
import com.unihub.workshop.registrations.domain.model.Registration;
import com.unihub.workshop.registrations.domain.model.RegistrationStatus;
import com.unihub.workshop.registrations.domain.model.RegistrationSummary;
import com.unihub.workshop.shared.exception.ValidationException;
import com.unihub.workshop.shared.pagination.CursorPage;
import com.unihub.workshop.workshops.api.WorkshopApi;
import com.unihub.workshop.workshops.api.dto.WorkshopRegistrationDetailsDto;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service implementing {@link GetMyRegistrationsUseCase}.
 * Resolves a student's registrations and enriches them in batch via {@link WorkshopApi}.
 */
@Service
@RequiredArgsConstructor
public class GetMyRegistrationsUseCaseImpl
    implements GetMyRegistrationsUseCase
{

    private final RegistrationRepository registrationRepository;
    private final WorkshopApi workshopApi;

    @Override
    @Transactional(readOnly = true)
    public CursorPage<RegistrationSummary> getMyRegistrations(
        GetMyRegistrationsQuery query
    ) {
        RegistrationStatus statusEnum = parseStatus(query.status());

        CursorPage<Registration> page =
            registrationRepository.findByStudentUserId(
                query.studentUserId(),
                statusEnum,
                query.cursor(),
                query.resolvedSize()
            );

        if (page == null || page.items().isEmpty()) {
            return CursorPage.empty();
        }

        Set<UUID> workshopIds = page
            .items()
            .stream()
            .map(Registration::workshopId)
            .collect(Collectors.toSet());

        Map<UUID, WorkshopRegistrationDetailsDto> workshops =
            workshopApi.getWorkshopsForRegistrations(workshopIds);

        List<RegistrationSummary> summaries = page
            .items()
            .stream()
            .map(reg -> {
                WorkshopRegistrationDetailsDto workshop = workshops.get(
                    reg.workshopId()
                );
                return new RegistrationSummary(
                    reg.id(),
                    reg.workshopId(),
                    workshop != null ? workshop.title() : null,
                    workshop != null ? workshop.startsAt() : null,
                    workshop != null ? workshop.endsAt() : null,
                    reg.status(),
                    reg.createdAt()
                );
            })
            .toList();

        return new CursorPage<>(summaries, page.nextCursor(), page.hasMore());
    }

    private RegistrationStatus parseStatus(String statusStr) {
        if (statusStr == null || statusStr.isBlank()) {
            return null;
        }
        try {
            return RegistrationStatus.valueOf(statusStr.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ValidationException(
                "status",
                "Invalid registration status: " + statusStr
            );
        }
    }
}
