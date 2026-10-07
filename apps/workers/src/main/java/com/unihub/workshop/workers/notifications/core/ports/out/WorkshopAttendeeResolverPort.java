package com.unihub.workshop.workers.notifications.core.ports.out;

import com.unihub.workshop.workers.notifications.core.models.WorkshopAttendeeRecipient;
import java.util.List;
import java.util.UUID;

public interface WorkshopAttendeeResolverPort {
    List<WorkshopAttendeeRecipient> findConfirmedAttendees(
        UUID workshopId,
        int page,
        int size
    );
}
