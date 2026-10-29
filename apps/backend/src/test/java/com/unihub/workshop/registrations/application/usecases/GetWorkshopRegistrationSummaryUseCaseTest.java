package com.unihub.workshop.registrations.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.unihub.workshop.registrations.application.ports.in.GetWorkshopRegistrationSummaryQuery;
import com.unihub.workshop.registrations.application.ports.out.RegistrationRepository;
import com.unihub.workshop.registrations.domain.model.WorkshopRegistrationRawCounts;
import com.unihub.workshop.registrations.domain.model.WorkshopRegistrationSeatCounts;
import com.unihub.workshop.shared.exception.ForbiddenException;
import com.unihub.workshop.shared.exception.NotFoundException;
import com.unihub.workshop.workshops.api.WorkshopApi;
import com.unihub.workshop.workshops.api.dto.WorkshopRegistrationDetailsDto;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GetWorkshopRegistrationSummaryUseCaseTest {

    @Mock
    private RegistrationRepository registrationRepository;

    @Mock
    private WorkshopApi workshopApi;

    @InjectMocks
    private GetWorkshopRegistrationSummaryUseCaseImpl useCase;

    private UUID workshopId;
    private UUID orgId;
    private Instant now;

    @BeforeEach
    void setUp() {
        workshopId = UUID.randomUUID();
        orgId = UUID.randomUUID();
        now = Instant.parse("2026-10-28T14:00:00Z");
    }

    private WorkshopRegistrationDetailsDto createWorkshopDto(
        UUID id,
        UUID organizationId,
        Integer capacity,
        Instant timestamp
    ) {
        return new WorkshopRegistrationDetailsDto(
            id,
            organizationId,
            "Test Workshop",
            "test-workshop",
            "PUBLISHED",
            timestamp,
            timestamp.plusSeconds(7200),
            BigDecimal.ZERO,
            capacity,
            "Room 101"
        );
    }

    @Test
    @DisplayName(
        "Scenario 1: Happy path returns calculated summary when workshop exists and caller org matches (FR-04..08)"
    )
    void shouldReturnSummary_whenWorkshopExistsAndOrgMatches() {
        // Arrange
        GetWorkshopRegistrationSummaryQuery query =
            new GetWorkshopRegistrationSummaryQuery(workshopId, orgId);
        WorkshopRegistrationDetailsDto workshop = createWorkshopDto(
            workshopId,
            orgId,
            100,
            now
        );
        Instant latestRegistrationTime = now.plusSeconds(300);

        when(workshopApi.getWorkshopForRegistration(workshopId)).thenReturn(
            Optional.of(workshop)
        );
        when(
            registrationRepository.getRegistrationRawCounts(workshopId)
        ).thenReturn(
            new WorkshopRegistrationRawCounts(
                72L,
                5L,
                Optional.of(latestRegistrationTime)
            )
        );

        // Act
        WorkshopRegistrationSeatCounts result = useCase.getSummary(query);

        // Assert
        assertThat(result.workshopId()).isEqualTo(workshopId);
        assertThat(result.capacity()).isEqualTo(100);
        assertThat(result.confirmedCount()).isEqualTo(72L);
        assertThat(result.pendingCount()).isEqualTo(5L);
        assertThat(result.availableSeats()).isEqualTo(23);
        assertThat(result.occupancyPercentage()).isEqualTo(
            new BigDecimal("72.00")
        );
        assertThat(result.lastUpdatedAt()).isEqualTo(latestRegistrationTime);

        verify(workshopApi).getWorkshopForRegistration(workshopId);
        verify(registrationRepository).getRegistrationRawCounts(workshopId);
    }

    @Test
    @DisplayName(
        "Scenario 2: Returns 0.00 occupancy and 0 available seats when workshop capacity is 0 (FR-06, FR-07, BR-08, BR-09)"
    )
    void shouldHandleZeroCapacityGracefully() {
        // Arrange
        GetWorkshopRegistrationSummaryQuery query =
            new GetWorkshopRegistrationSummaryQuery(workshopId, orgId);
        WorkshopRegistrationDetailsDto workshop = createWorkshopDto(
            workshopId,
            orgId,
            0,
            now
        );

        when(workshopApi.getWorkshopForRegistration(workshopId)).thenReturn(
            Optional.of(workshop)
        );
        when(
            registrationRepository.getRegistrationRawCounts(workshopId)
        ).thenReturn(
            new WorkshopRegistrationRawCounts(0L, 0L, Optional.of(now))
        );

        // Act
        WorkshopRegistrationSeatCounts result = useCase.getSummary(query);

        // Assert
        assertThat(result.capacity()).isEqualTo(0);
        assertThat(result.availableSeats()).isEqualTo(0);
        assertThat(result.occupancyPercentage()).isEqualTo(
            new BigDecimal("0.00")
        );
    }

    @Test
    @DisplayName(
        "Scenario 3: Falls back to workshop timestamp when no registrations exist (FR-08)"
    )
    void shouldFallbackToWorkshopTimestamp_whenNoRegistrationsExist() {
        // Arrange
        GetWorkshopRegistrationSummaryQuery query =
            new GetWorkshopRegistrationSummaryQuery(workshopId, orgId);
        WorkshopRegistrationDetailsDto workshop = createWorkshopDto(
            workshopId,
            orgId,
            50,
            now
        );

        when(workshopApi.getWorkshopForRegistration(workshopId)).thenReturn(
            Optional.of(workshop)
        );
        when(
            registrationRepository.getRegistrationRawCounts(workshopId)
        ).thenReturn(
            new WorkshopRegistrationRawCounts(0L, 0L, Optional.empty())
        );

        // Act
        WorkshopRegistrationSeatCounts result = useCase.getSummary(query);

        // Assert
        assertThat(result.confirmedCount()).isEqualTo(0L);
        assertThat(result.pendingCount()).isEqualTo(0L);
        assertThat(result.availableSeats()).isEqualTo(50);
        assertThat(result.occupancyPercentage()).isEqualTo(
            new BigDecimal("0.00")
        );
        assertThat(result.lastUpdatedAt()).isEqualTo(now);
    }

    @Test
    @DisplayName(
        "Scenario 4: Throws NotFoundException when workshop does not exist (FR-02)"
    )
    void shouldThrowNotFoundException_whenWorkshopDoesNotExist() {
        // Arrange
        GetWorkshopRegistrationSummaryQuery query =
            new GetWorkshopRegistrationSummaryQuery(workshopId, orgId);
        when(workshopApi.getWorkshopForRegistration(workshopId)).thenReturn(
            Optional.empty()
        );

        // Act & Assert
        assertThatThrownBy(() -> useCase.getSummary(query))
            .isInstanceOf(NotFoundException.class)
            .hasMessageContaining("Workshop not found");

        verifyNoInteractions(registrationRepository);
    }

    @Test
    @DisplayName(
        "Scenario 5: Throws ForbiddenException when caller org does not match workshop org (FR-03, BR-01)"
    )
    void shouldThrowForbiddenException_whenCallerOrgDoesNotMatchWorkshopOrg() {
        // Arrange
        UUID differentOrgId = UUID.randomUUID();
        GetWorkshopRegistrationSummaryQuery query =
            new GetWorkshopRegistrationSummaryQuery(workshopId, orgId);
        WorkshopRegistrationDetailsDto workshop = createWorkshopDto(
            workshopId,
            differentOrgId,
            100,
            now
        );

        when(workshopApi.getWorkshopForRegistration(workshopId)).thenReturn(
            Optional.of(workshop)
        );

        // Act & Assert
        assertThatThrownBy(() -> useCase.getSummary(query))
            .isInstanceOf(ForbiddenException.class)
            .hasMessageContaining("Forbidden");

        verifyNoInteractions(registrationRepository);
    }
}
