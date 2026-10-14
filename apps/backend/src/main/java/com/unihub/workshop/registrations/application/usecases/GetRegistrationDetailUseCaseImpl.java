package com.unihub.workshop.registrations.application.usecases;

import com.unihub.workshop.registrations.application.ports.in.GetRegistrationDetailUseCase;
import com.unihub.workshop.registrations.application.ports.out.RegistrationRepository;
import com.unihub.workshop.registrations.domain.model.Registration;
import com.unihub.workshop.registrations.domain.model.RegistrationDetail;
import com.unihub.workshop.registrations.domain.model.RegistrationStatus;
import com.unihub.workshop.shared.exception.ForbiddenException;
import com.unihub.workshop.shared.exception.NotFoundException;
import com.unihub.workshop.workshops.api.WorkshopApi;
import com.unihub.workshop.workshops.api.dto.WorkshopRegistrationDetailsDto;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GetRegistrationDetailUseCaseImpl
    implements GetRegistrationDetailUseCase
{

    private final RegistrationRepository registrationRepository;
    private final WorkshopApi workshopApi;

    @Override
    public RegistrationDetail getRegistrationDetail(
        UUID studentUserId,
        UUID registrationId
    ) {
        Registration registration = registrationRepository
            .findById(registrationId)
            .orElseThrow(() ->
                new NotFoundException(
                    "Registration not found with ID: " + registrationId
                )
            );

        if (!registration.studentUserId().equals(studentUserId)) {
            throw new ForbiddenException(
                "You do not have permission to view this registration"
            );
        }

        Optional<WorkshopRegistrationDetailsDto> workshopOpt =
            workshopApi.getWorkshopForRegistration(registration.workshopId());

        String workshopTitle = workshopOpt
            .map(WorkshopRegistrationDetailsDto::title)
            .orElse("Unknown Workshop");
        Instant workshopStartsAt = workshopOpt
            .map(WorkshopRegistrationDetailsDto::startsAt)
            .orElse(null);
        Instant workshopEndsAt = workshopOpt
            .map(WorkshopRegistrationDetailsDto::endsAt)
            .orElse(null);
        String workshopLocation = workshopOpt
            .map(WorkshopRegistrationDetailsDto::location)
            .orElse(null);

        String qrPayload = null;
        String qrSignature = null;
        Instant qrIssuedAt = null;

        if (
            registration.status() == RegistrationStatus.CONFIRMED &&
            registration.qrTicket() != null
        ) {
            qrPayload = registration.qrTicket().qrPayload();
            qrSignature = registration.qrTicket().qrSignature();
            qrIssuedAt = registration.qrTicket().qrIssuedAt();
        }

        return RegistrationDetail.builder()
            .id(registration.id())
            .workshopId(registration.workshopId())
            .workshopTitle(workshopTitle)
            .workshopStartsAt(workshopStartsAt)
            .workshopEndsAt(workshopEndsAt)
            .workshopLocation(workshopLocation)
            .studentId(registration.studentUserId())
            .status(registration.status())
            .createdAt(registration.createdAt())
            .qrPayload(qrPayload)
            .qrSignature(qrSignature)
            .qrIssuedAt(qrIssuedAt)
            .build();
    }
}
