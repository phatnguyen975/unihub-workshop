package com.unihub.workshop.workers.notifications.outbound.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.unihub.workshop.workers.notifications.core.models.OrganizerRecipient;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrganizationRecipientResolverAdapterTest {

    @Mock
    private JpaUserRepository userRepository;

    @InjectMocks
    private OrganizationRecipientResolverAdapter adapter;

    @Test
    @DisplayName("Should resolve active organizers for given organizationId")
    void shouldResolveActiveOrganizers() {
        UUID orgId = UUID.randomUUID();
        UUID userId1 = UUID.randomUUID();
        UUID userId2 = UUID.randomUUID();

        UserEntity user1 = UserEntity.builder()
            .id(userId1)
            .organizationId(orgId)
            .role("ORGANIZER")
            .email("organizer1@example.com")
            .fullName("First Organizer")
            .isActive(true)
            .build();

        UserEntity user2 = UserEntity.builder()
            .id(userId2)
            .organizationId(orgId)
            .role("ORGANIZER")
            .email("organizer2@example.com")
            .fullName("Second Organizer")
            .isActive(true)
            .build();

        when(
            userRepository.findActiveOrganizersByOrganizationId(orgId)
        ).thenReturn(List.of(user1, user2));

        List<OrganizerRecipient> recipients = adapter.findActiveOrganizers(
            orgId
        );

        assertThat(recipients).hasSize(2);
        assertThat(recipients.get(0).id()).isEqualTo(userId1);
        assertThat(recipients.get(0).email()).isEqualTo(
            "organizer1@example.com"
        );
        assertThat(recipients.get(0).fullName()).isEqualTo("First Organizer");
        assertThat(recipients.get(1).id()).isEqualTo(userId2);
        assertThat(recipients.get(1).email()).isEqualTo(
            "organizer2@example.com"
        );
        assertThat(recipients.get(1).fullName()).isEqualTo("Second Organizer");
    }

    @Test
    @DisplayName("Should return empty list when organizationId is null")
    void shouldReturnEmptyWhenOrgIdNull() {
        List<OrganizerRecipient> recipients = adapter.findActiveOrganizers(
            null
        );

        assertThat(recipients).isEmpty();
        verify(userRepository, never()).findActiveOrganizersByOrganizationId(
            any()
        );
    }

    @Test
    @DisplayName("Should return empty list when no active organizers exist")
    void shouldReturnEmptyWhenNoOrganizersFound() {
        UUID orgId = UUID.randomUUID();
        when(
            userRepository.findActiveOrganizersByOrganizationId(orgId)
        ).thenReturn(List.of());

        List<OrganizerRecipient> recipients = adapter.findActiveOrganizers(
            orgId
        );

        assertThat(recipients).isEmpty();
    }
}
