package com.unihub.workshop.workshops.application.ports.in;

import com.unihub.workshop.workshops.domain.model.Workshop;
import java.util.UUID;

public interface GetWorkshopUseCase {
    Workshop execute(UUID id, UUID callerOrganizationId);
}
