package com.unihub.workshop.workers.batch_ingestion.outbound.persistence;

import com.unihub.workshop.workers.batch_ingestion.core.ports.out.OrganizationDirectory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class PostgresOrganizationDirectory implements OrganizationDirectory {

    private final OrganizationRepository repository;

    @Override
    public Optional<OrganizationInfo> findBySlug(String slug) {
        return repository.findBySlug(slug)
                .map(entity -> new OrganizationInfo(entity.getId(), entity.getName()));
    }
}
