package com.unihub.workshop.workshops.application.ports.in;

import java.util.UUID;

public interface DeleteDraftUseCase {
    void execute(UUID id, UUID callerOrganizationId);
}
