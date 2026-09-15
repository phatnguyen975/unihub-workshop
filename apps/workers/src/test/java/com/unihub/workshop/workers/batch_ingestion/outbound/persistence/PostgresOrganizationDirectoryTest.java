package com.unihub.workshop.workers.batch_ingestion.outbound.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.unihub.workshop.workers.batch_ingestion.core.ports.out.OrganizationDirectory;
import com.unihub.workshop.workers.batch_ingestion.outbound.persistence.mapper.OrganizationPersistenceMapper;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

class PostgresOrganizationDirectoryTest {

    private final OrganizationPersistenceMapper mapper = Mappers.getMapper(
        OrganizationPersistenceMapper.class
    );

    @Test
    void shouldFindAndMapOrganization() {
        OrganizationRepository repository = mock(OrganizationRepository.class);
        PostgresOrganizationDirectory directory =
            new PostgresOrganizationDirectory(repository, mapper);

        UUID orgId = UUID.randomUUID();
        OrganizationEntity entity = new OrganizationEntity();
        entity.setId(orgId);
        entity.setName("Information Technology");
        entity.setSlug("it");

        when(repository.findBySlugAndStatus("it", "ACTIVE")).thenReturn(
            Optional.of(entity)
        );

        Optional<OrganizationDirectory.OrganizationInfo> result =
            directory.findActiveBySlug("it");

        assertThat(result).isPresent();
        assertThat(result.get().id()).isEqualTo(orgId);
        assertThat(result.get().name()).isEqualTo("Information Technology");
    }

    @Test
    void shouldReturnEmptyWhenNotFound() {
        OrganizationRepository repository = mock(OrganizationRepository.class);
        PostgresOrganizationDirectory directory =
            new PostgresOrganizationDirectory(repository, mapper);

        when(repository.findBySlugAndStatus("unknown", "ACTIVE")).thenReturn(
            Optional.empty()
        );

        Optional<OrganizationDirectory.OrganizationInfo> result =
            directory.findActiveBySlug("unknown");

        assertThat(result).isEmpty();
    }

    @Test
    void shouldReturnEmptyWhenOrganizationIsInactive() {
        OrganizationRepository repository = mock(OrganizationRepository.class);
        PostgresOrganizationDirectory directory =
            new PostgresOrganizationDirectory(repository, mapper);

        when(
            repository.findBySlugAndStatus("inactive-org", "ACTIVE")
        ).thenReturn(Optional.empty());

        Optional<OrganizationDirectory.OrganizationInfo> result =
            directory.findActiveBySlug("inactive-org");

        assertThat(result).isEmpty();
    }
}
