package com.unihub.workshop.workshops.application.ports.in;

import java.util.UUID;

import com.unihub.workshop.workshops.application.usecases.RejectArtifactUrlResult;

public interface GetRejectArtifactUrlUseCase {
    RejectArtifactUrlResult execute(UUID jobId, UUID organizationId);
}
