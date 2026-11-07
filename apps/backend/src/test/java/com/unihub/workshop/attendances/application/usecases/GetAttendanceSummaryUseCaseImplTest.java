package com.unihub.workshop.attendances.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.unihub.workshop.attendances.application.ports.in.GetAttendanceSummaryQuery;
import com.unihub.workshop.attendances.application.ports.out.AttendanceRepository;
import com.unihub.workshop.attendances.domain.model.AttendanceSummary;
import com.unihub.workshop.registrations.api.RegistrationApi;
import com.unihub.workshop.shared.exception.ForbiddenException;
import com.unihub.workshop.shared.exception.NotFoundException;
import com.unihub.workshop.workshops.api.WorkshopApi;
import com.unihub.workshop.workshops.api.dto.WorkshopRegistrationDetailsDto;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;

class GetAttendanceSummaryUseCaseImplTest {

    private final WorkshopApi workshops = mock(WorkshopApi.class);
    private final RegistrationApi registrations = mock(RegistrationApi.class);
    private final AttendanceRepository attendances = mock(
        AttendanceRepository.class
    );
    private final PlatformTransactionManager manager = mock(
        PlatformTransactionManager.class
    );
    private final TransactionStatus primary = mock(TransactionStatus.class);
    private final TransactionStatus replica = mock(TransactionStatus.class);
    private final UUID workshopId = UUID.randomUUID();
    private final UUID orgId = UUID.randomUUID();
    private GetAttendanceSummaryUseCaseImpl useCase;

    @BeforeEach
    void setup() {
        when(manager.getTransaction(any())).thenReturn(primary, replica);
        useCase = new GetAttendanceSummaryUseCaseImpl(
            workshops,
            registrations,
            attendances,
            manager
        );
    }

    private WorkshopRegistrationDetailsDto workshop(
        UUID owner,
        Integer capacity
    ) {
        return new WorkshopRegistrationDetailsDto(
            workshopId,
            owner,
            "Title",
            "slug",
            "DRAFT",
            null,
            null,
            BigDecimal.ZERO,
            capacity,
            null
        );
    }

    @Test
    void authorizedReadCompletesPrimaryBeforeReplicaAndReturnsRealCounts() {
        when(workshops.getWorkshopForRegistration(workshopId)).thenReturn(
            Optional.of(workshop(orgId, 100))
        );
        when(registrations.countConfirmedByWorkshopId(workshopId)).thenReturn(
            80L
        );
        when(attendances.countValidByWorkshopId(workshopId)).thenReturn(50L);
        when(attendances.findRecentValidByWorkshopId(workshopId)).thenReturn(
            List.of()
        );

        AttendanceSummary summary = useCase.execute(
            new GetAttendanceSummaryQuery(workshopId, orgId)
        );

        assertThat(summary.checkInPercentage()).isEqualTo(
            new BigDecimal("62.50")
        );
        InOrder order = inOrder(manager, workshops, registrations, attendances);
        order.verify(manager).getTransaction(any(TransactionDefinition.class));
        order.verify(workshops).getWorkshopForRegistration(workshopId);
        order.verify(manager).commit(primary);
        order.verify(manager).getTransaction(any(TransactionDefinition.class));
        order.verify(registrations).countConfirmedByWorkshopId(workshopId);
        order.verify(attendances).countValidByWorkshopId(workshopId);
        order.verify(attendances).findRecentValidByWorkshopId(workshopId);
        order.verify(manager).commit(replica);
        ArgumentCaptor<TransactionDefinition> definitions =
            ArgumentCaptor.forClass(TransactionDefinition.class);
        verify(manager, times(2)).getTransaction(definitions.capture());
        assertThat(definitions.getAllValues().get(0).isReadOnly()).isFalse();
        assertThat(definitions.getAllValues().get(1).isReadOnly()).isTrue();
        assertThat(definitions.getAllValues()).allSatisfy(definition ->
            assertThat(definition.getPropagationBehavior()).isEqualTo(
                TransactionDefinition.PROPAGATION_REQUIRES_NEW
            )
        );
    }

    @Test
    void foreignWorkshopIsForbiddenBeforeReporting() {
        when(workshops.getWorkshopForRegistration(workshopId)).thenReturn(
            Optional.of(workshop(UUID.randomUUID(), 10))
        );
        assertThatThrownBy(() ->
            useCase.execute(new GetAttendanceSummaryQuery(workshopId, orgId))
        ).isInstanceOf(ForbiddenException.class);
        verify(registrations, never()).countConfirmedByWorkshopId(any());
        verify(attendances, never()).countValidByWorkshopId(any());
    }

    @Test
    void missingWorkshopReturnsNotFound() {
        when(workshops.getWorkshopForRegistration(workshopId)).thenReturn(
            Optional.empty()
        );
        assertThatThrownBy(() ->
            useCase.execute(new GetAttendanceSummaryQuery(workshopId, orgId))
        ).isInstanceOf(NotFoundException.class);
        verify(registrations, never()).countConfirmedByWorkshopId(any());
    }

    @Test
    void emptyDraftHasZeroCapacityAndCounts() {
        when(workshops.getWorkshopForRegistration(workshopId)).thenReturn(
            Optional.of(workshop(orgId, null))
        );
        when(attendances.findRecentValidByWorkshopId(workshopId)).thenReturn(
            List.of()
        );
        AttendanceSummary result = useCase.execute(
            new GetAttendanceSummaryQuery(workshopId, orgId)
        );
        assertThat(result.capacity()).isZero();
        assertThat(result.confirmedCount()).isZero();
        assertThat(result.checkedInCount()).isZero();
        assertThat(result.checkInPercentage()).isEqualTo(
            new BigDecimal("0.00")
        );
        assertThat(result.recentScans()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = { "DRAFT", "PUBLISHED", "COMPLETED", "CANCELED" })
    void organizerCanReadEveryWorkshopState(String state) {
        WorkshopRegistrationDetailsDto existing =
            new WorkshopRegistrationDetailsDto(
                workshopId,
                orgId,
                "Title",
                "slug",
                state,
                null,
                null,
                BigDecimal.ZERO,
                10,
                null
            );
        when(workshops.getWorkshopForRegistration(workshopId)).thenReturn(
            Optional.of(existing)
        );
        when(attendances.findRecentValidByWorkshopId(workshopId)).thenReturn(
            List.of()
        );
        assertThat(
            useCase
                .execute(new GetAttendanceSummaryQuery(workshopId, orgId))
                .workshopId()
        ).isEqualTo(workshopId);
    }
}
