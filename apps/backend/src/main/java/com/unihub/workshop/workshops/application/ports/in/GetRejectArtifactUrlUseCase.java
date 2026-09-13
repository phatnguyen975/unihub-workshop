package com.unihub.workshop.workshops.application.ports.in;

import com.unihub.workshop.workshops.application.usecases.RejectArtifactUrlResult;
import java.util.UUID;

public interface GetRejectArtifactUrlUseCase {
    RejectArtifactUrlResult execute(UUID jobId, UUID organizationId);
}
