package com.unihub.workshop.workers.batch_ingestion.outbound.persistence;

import com.unihub.workshop.workers.batch_ingestion.core.ports.out.OrganizationDirectory;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PostgresOrganizationDirectory implements OrganizationDirectory {

    private final OrganizationRepository repository;

    @Override
    public Optional<OrganizationInfo> findActiveBySlug(String slug) {
        return repository
            .findBySlugAndStatus(slug, "ACTIVE")
            .map(entity ->
                new OrganizationInfo(entity.getId(), entity.getName())
            );
    }
}
