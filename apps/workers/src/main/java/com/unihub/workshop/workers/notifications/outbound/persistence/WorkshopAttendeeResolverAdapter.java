package com.unihub.workshop.workers.notifications.outbound.persistence;

import com.unihub.workshop.workers.notifications.core.models.WorkshopAttendeeRecipient;
import com.unihub.workshop.workers.notifications.core.ports.out.WorkshopAttendeeResolverPort;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class WorkshopAttendeeResolverAdapter
    implements WorkshopAttendeeResolverPort
{

    private final JpaRegistrationRepository registrationRepository;

    @Override
    public List<WorkshopAttendeeRecipient> findConfirmedAttendees(
        UUID workshopId,
        int page,
        int size
    ) {
        if (workshopId == null) {
            log.warn("Cannot resolve attendees for null workshopId");
            return Collections.emptyList();
        }

        Pageable pageable = PageRequest.of(page, size);
        Slice<AttendeeProjection> resultSlice =
            registrationRepository.findConfirmedAttendeesByWorkshopId(
                workshopId,
                pageable
            );

        return resultSlice
            .getContent()
            .stream()
            .map(p ->
                new WorkshopAttendeeRecipient(p.id(), p.email(), p.fullName())
            )
            .toList();
    }
}
