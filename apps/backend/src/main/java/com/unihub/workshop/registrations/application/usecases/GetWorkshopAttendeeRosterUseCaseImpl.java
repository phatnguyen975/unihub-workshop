package com.unihub.workshop.registrations.application.usecases;

import com.unihub.workshop.iam.api.IamApi;
import com.unihub.workshop.iam.api.dto.StudentProfileDto;
import com.unihub.workshop.registrations.application.ports.in.GetWorkshopAttendeeRosterQuery;
import com.unihub.workshop.registrations.application.ports.in.GetWorkshopAttendeeRosterUseCase;
import com.unihub.workshop.registrations.application.ports.out.RegistrationRepository;
import com.unihub.workshop.registrations.domain.model.Registration;
import com.unihub.workshop.registrations.domain.model.RegistrationStatus;
import com.unihub.workshop.registrations.domain.model.WorkshopAttendeeRosterItem;
import com.unihub.workshop.shared.exception.ForbiddenException;
import com.unihub.workshop.shared.exception.NotFoundException;
import com.unihub.workshop.shared.exception.ValidationException;
import com.unihub.workshop.shared.pagination.CursorPage;
import com.unihub.workshop.workshops.api.WorkshopApi;
import com.unihub.workshop.workshops.api.dto.WorkshopRegistrationDetailsDto;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class GetWorkshopAttendeeRosterUseCaseImpl
    implements GetWorkshopAttendeeRosterUseCase
{

    private final RegistrationRepository registrationRepository;
    private final WorkshopApi workshopApi;
    private final IamApi iamApi;

    @Override
    @Transactional(readOnly = true)
    public CursorPage<WorkshopAttendeeRosterItem> getRoster(
        GetWorkshopAttendeeRosterQuery query
    ) {
        log.debug(
            "Retrieving attendee roster for workshop [{}] with status filter [{}] and size [{}]",
            query.workshopId(),
            query.status(),
            query.size()
        );

        // 1. Validate page size
        int size = query.size() != null ? query.size() : 20;
        if (size < 1 || size > 100) {
            throw new ValidationException(
                "size",
                "size must be between 1 and 100"
            );
        }

        // 2. Validate and parse optional status filter
        RegistrationStatus status = parseStatusFilter(query.status());

        // 3. Workshop existence check
        WorkshopRegistrationDetailsDto workshop = workshopApi
            .getWorkshopForRegistration(query.workshopId())
            .orElseThrow(() ->
                new NotFoundException(
                    "Workshop not found with ID: " + query.workshopId()
                )
            );

        // 4. Tenant isolation check: workshop must belong to caller's org
        if (!query.callerOrganizationId().equals(workshop.organizationId())) {
            log.warn(
                "Access denied: caller org [{}] does not match workshop org [{}] for workshop [{}]",
                query.callerOrganizationId(),
                workshop.organizationId(),
                query.workshopId()
            );
            throw new ForbiddenException(
                "Forbidden: Workshop does not belong to caller's organization"
            );
        }

        // 5. Query keyset paginated registrations from read replica
        CursorPage<Registration> registrationPage =
            registrationRepository.findByWorkshopId(
                query.workshopId(),
                status,
                query.cursor(),
                size
            );

        // 6. Batch resolve student profiles via IamApi
        List<Registration> registrations = registrationPage.items();
        Set<UUID> studentUserIds = registrations
            .stream()
            .map(Registration::studentUserId)
            .collect(Collectors.toSet());

        Map<UUID, StudentProfileDto> studentProfiles = studentUserIds.isEmpty()
            ? Collections.emptyMap()
            : iamApi.getStudentProfilesByIds(studentUserIds);

        // 7. Map to domain WorkshopAttendeeRosterItem with nested student profile
        List<WorkshopAttendeeRosterItem> rosterItems = registrations
            .stream()
            .map(reg -> {
                StudentProfileDto profile = studentProfiles.get(
                    reg.studentUserId()
                );
                WorkshopAttendeeRosterItem.StudentProfileSummary studentSummary =
                    profile != null
                        ? new WorkshopAttendeeRosterItem.StudentProfileSummary(
                              profile.userId(),
                              profile.fullName(),
                              profile.studentNumber(),
                              profile.email()
                          )
                        : new WorkshopAttendeeRosterItem.StudentProfileSummary(
                              reg.studentUserId(),
                              null,
                              null,
                              null
                          );

                return new WorkshopAttendeeRosterItem(
                    reg.id(),
                    studentSummary,
                    reg.status(),
                    reg.priceAtRegistration(),
                    reg.currency(),
                    reg.createdAt()
                );
            })
            .toList();

        return new CursorPage<>(
            rosterItems,
            registrationPage.nextCursor(),
            registrationPage.hasMore()
        );
    }

    private RegistrationStatus parseStatusFilter(String statusStr) {
        if (statusStr == null || statusStr.isBlank()) {
            return null;
        }
        try {
            return RegistrationStatus.valueOf(statusStr.trim());
        } catch (IllegalArgumentException e) {
            throw new ValidationException(
                "status",
                "Invalid registration status: " + statusStr
            );
        }
    }
}
