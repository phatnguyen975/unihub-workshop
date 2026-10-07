package com.unihub.workshop.workers.notifications.outbound.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.unihub.workshop.workers.notifications.core.models.WorkshopAttendeeRecipient;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class WorkshopAttendeeResolverAdapterTest {

    @Mock
    private JpaRegistrationRepository registrationRepository;

    @InjectMocks
    private WorkshopAttendeeResolverAdapter adapter;

    @Test
    @DisplayName(
        "Should resolve confirmed attendees for given workshopId and pagination parameters"
    )
    void shouldResolveConfirmedAttendees() {
        UUID workshopId = UUID.randomUUID();
        UUID userId1 = UUID.randomUUID();
        UUID userId2 = UUID.randomUUID();

        AttendeeProjection p1 = new AttendeeProjection(
            userId1,
            "student1@example.com",
            "Student One"
        );
        AttendeeProjection p2 = new AttendeeProjection(
            userId2,
            "student2@example.com",
            "Student Two"
        );
        Page<AttendeeProjection> page = new PageImpl<>(
            List.of(p1, p2),
            PageRequest.of(0, 500),
            2
        );

        when(
            registrationRepository.findConfirmedAttendeesByWorkshopId(
                eq(workshopId),
                any(Pageable.class)
            )
        ).thenReturn(page);

        List<WorkshopAttendeeRecipient> recipients =
            adapter.findConfirmedAttendees(workshopId, 0, 500);

        assertThat(recipients).hasSize(2);
        assertThat(recipients.get(0).id()).isEqualTo(userId1);
        assertThat(recipients.get(0).email()).isEqualTo("student1@example.com");
        assertThat(recipients.get(0).fullName()).isEqualTo("Student One");
        assertThat(recipients.get(1).id()).isEqualTo(userId2);
        assertThat(recipients.get(1).email()).isEqualTo("student2@example.com");
        assertThat(recipients.get(1).fullName()).isEqualTo("Student Two");

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(
            Pageable.class
        );
        verify(registrationRepository).findConfirmedAttendeesByWorkshopId(
            eq(workshopId),
            captor.capture()
        );
        assertThat(captor.getValue().getPageNumber()).isEqualTo(0);
        assertThat(captor.getValue().getPageSize()).isEqualTo(500);
    }

    @Test
    @DisplayName("Should return empty list when workshopId is null")
    void shouldReturnEmptyWhenWorkshopIdNull() {
        List<WorkshopAttendeeRecipient> recipients =
            adapter.findConfirmedAttendees(null, 0, 100);

        assertThat(recipients).isEmpty();
        verify(
            registrationRepository,
            never()
        ).findConfirmedAttendeesByWorkshopId(any(), any());
    }

    @Test
    @DisplayName("Should return empty list when no confirmed attendees exist")
    void shouldReturnEmptyWhenNoAttendeesFound() {
        UUID workshopId = UUID.randomUUID();
        when(
            registrationRepository.findConfirmedAttendeesByWorkshopId(
                eq(workshopId),
                any(Pageable.class)
            )
        ).thenReturn(Page.empty());

        List<WorkshopAttendeeRecipient> recipients =
            adapter.findConfirmedAttendees(workshopId, 0, 100);

        assertThat(recipients).isEmpty();
    }
}
