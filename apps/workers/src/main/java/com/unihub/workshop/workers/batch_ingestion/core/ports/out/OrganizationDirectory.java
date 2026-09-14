package com.unihub.workshop.workers.batch_ingestion.core.ports.out;

import java.util.Optional;
import java.util.UUID;

public interface OrganizationDirectory {
    Optional<OrganizationInfo> findActiveBySlug(String slug);

    record OrganizationInfo(UUID id, String name) {}
}
