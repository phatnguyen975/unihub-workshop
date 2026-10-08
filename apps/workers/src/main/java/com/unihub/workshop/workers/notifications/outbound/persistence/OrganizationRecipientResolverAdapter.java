package com.unihub.workshop.workers.notifications.outbound.persistence;

import com.unihub.workshop.workers.notifications.core.models.OrganizerRecipient;
import com.unihub.workshop.workers.notifications.core.ports.out.OrganizationRecipientResolverPort;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrganizationRecipientResolverAdapter
    implements OrganizationRecipientResolverPort
{

    private final JpaUserRepository userRepository;

    @Override
    public List<OrganizerRecipient> findActiveOrganizers(UUID organizationId) {
        if (organizationId == null) {
            log.warn("Cannot resolve organizers for null organizationId");
            return Collections.emptyList();
        }

        List<UserEntity> entities =
            userRepository.findActiveOrganizersByOrganizationId(organizationId);

        return entities
            .stream()
            .map(u ->
                new OrganizerRecipient(u.getId(), u.getEmail(), u.getFullName())
            )
            .toList();
    }
}
