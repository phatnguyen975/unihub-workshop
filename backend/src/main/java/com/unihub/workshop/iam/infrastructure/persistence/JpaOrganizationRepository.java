package com.unihub.workshop.iam.infrastructure.persistence;

import com.unihub.workshop.iam.domain.model.Organization;
import com.unihub.workshop.iam.domain.repository.OrganizationRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface JpaOrganizationRepository extends JpaRepository<Organization, UUID>, OrganizationRepository {

    @Override
    Optional<Organization> findByOrganizerId(UUID organizerId);
}
