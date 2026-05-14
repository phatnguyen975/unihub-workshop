package com.unihub.workshop.iam.domain.repository;

import com.unihub.workshop.iam.domain.model.Organization;
import java.util.Optional;
import java.util.UUID;

public interface OrganizationRepository {

    Optional<Organization> findById(UUID id);

    Optional<Organization> findByOrganizerId(UUID organizerId);

    Organization save(Organization organization);
}
