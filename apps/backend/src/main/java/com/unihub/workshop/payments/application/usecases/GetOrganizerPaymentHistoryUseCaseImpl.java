package com.unihub.workshop.payments.application.usecases;

import com.unihub.workshop.iam.api.IamApi;
import com.unihub.workshop.iam.api.dto.StudentProfileDto;
import com.unihub.workshop.payments.application.ports.in.GetOrganizerPaymentHistoryQuery;
import com.unihub.workshop.payments.application.ports.in.GetOrganizerPaymentHistoryUseCase;
import com.unihub.workshop.payments.application.ports.out.PaymentRepository;
import com.unihub.workshop.payments.domain.model.OrganizerPaymentHistoryItem;
import com.unihub.workshop.payments.domain.model.Payment;
import com.unihub.workshop.payments.domain.model.PaymentStatus;
import com.unihub.workshop.registrations.api.RegistrationApi;
import com.unihub.workshop.registrations.api.dto.RegistrationPaymentContextDto;
import com.unihub.workshop.shared.exception.BadRequestException;
import com.unihub.workshop.shared.exception.NotFoundException;
import com.unihub.workshop.shared.pagination.CursorPage;
import com.unihub.workshop.workshops.api.WorkshopApi;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service implementing {@link GetOrganizerPaymentHistoryUseCase}.
 *
 * <p>Resolves the tenant-scoped payment page from this module, then enriches it with the
 * linked registration contexts and the minimal student identity in one batch each.
 */
@Service
@RequiredArgsConstructor
public class GetOrganizerPaymentHistoryUseCaseImpl
    implements GetOrganizerPaymentHistoryUseCase
{

    private static final String WORKSHOP_ID_FIELD = "workshopId";

    private final PaymentRepository paymentRepository;
    private final RegistrationApi registrationApi;
    private final IamApi iamApi;
    private final WorkshopApi workshopApi;

    @Override
    @Transactional(readOnly = true)
    public CursorPage<OrganizerPaymentHistoryItem> getPaymentHistory(
        GetOrganizerPaymentHistoryQuery query
    ) {
        PaymentStatus status = PaymentHistoryQueryParser.parseStatus(
            query.status()
        );
        int size = PaymentHistoryQueryParser.parseSize(query.size());
        UUID workshopId = parseWorkshopId(query.workshopId());
        verifyWorkshopAccessible(workshopId, query.organizationId());

        CursorPage<Payment> page = paymentRepository.findByOrganizationId(
            query.organizationId(),
            workshopId,
            status,
            query.cursor(),
            size
        );

        if (page == null || page.items().isEmpty()) {
            return CursorPage.empty();
        }

        Set<UUID> registrationIds = page
            .items()
            .stream()
            .map(Payment::registrationId)
            .collect(Collectors.toSet());

        Map<UUID, RegistrationPaymentContextDto> contexts =
            registrationApi.getPaymentContexts(registrationIds);
        Map<UUID, StudentProfileDto> profiles = resolveStudentProfiles(
            contexts
        );

        List<OrganizerPaymentHistoryItem> items = page
            .items()
            .stream()
            .map(payment -> toHistoryItem(payment, contexts, profiles))
            .toList();

        return new CursorPage<>(items, page.nextCursor(), page.hasMore());
    }

    private UUID parseWorkshopId(String workshopId) {
        if (workshopId == null) {
            return null;
        }
        try {
            return UUID.fromString(workshopId.trim());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(
                "Invalid '" + WORKSHOP_ID_FIELD + "' value: " + workshopId
            );
        }
    }

    private void verifyWorkshopAccessible(
        UUID workshopId,
        UUID organizationId
    ) {
        if (workshopId == null) {
            return;
        }
        boolean accessible = workshopApi
            .getWorkshopForRegistration(workshopId)
            .filter(workshop ->
                organizationId.equals(workshop.organizationId())
            )
            .isPresent();
        if (!accessible) {
            throw new NotFoundException("Workshop not found");
        }
    }

    private Map<UUID, StudentProfileDto> resolveStudentProfiles(
        Map<UUID, RegistrationPaymentContextDto> contexts
    ) {
        Set<UUID> studentUserIds = contexts
            .values()
            .stream()
            .map(RegistrationPaymentContextDto::studentUserId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        if (studentUserIds.isEmpty()) {
            return Map.of();
        }
        return iamApi.getStudentProfilesByIds(studentUserIds);
    }

    private OrganizerPaymentHistoryItem toHistoryItem(
        Payment payment,
        Map<UUID, RegistrationPaymentContextDto> contexts,
        Map<UUID, StudentProfileDto> profiles
    ) {
        RegistrationPaymentContextDto context = contexts.get(
            payment.registrationId()
        );
        UUID studentUserId = context != null ? context.studentUserId() : null;
        StudentProfileDto profile =
            studentUserId != null ? profiles.get(studentUserId) : null;
        return new OrganizerPaymentHistoryItem(
            payment,
            context != null ? context.workshopId() : null,
            context != null ? context.status() : null,
            profile != null ? profile.fullName() : null,
            profile != null ? profile.studentNumber() : null
        );
    }
}
