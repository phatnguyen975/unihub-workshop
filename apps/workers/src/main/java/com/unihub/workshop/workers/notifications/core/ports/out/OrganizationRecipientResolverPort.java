package com.unihub.workshop.workers.notifications.core.ports.out;

import com.unihub.workshop.workers.notifications.core.models.OrganizerRecipient;
import java.util.List;
import java.util.UUID;

public interface OrganizationRecipientResolverPort {
    List<OrganizerRecipient> findActiveOrganizers(UUID organizationId);
}
