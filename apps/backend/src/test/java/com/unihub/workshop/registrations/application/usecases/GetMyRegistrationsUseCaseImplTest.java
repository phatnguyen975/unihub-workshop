package com.unihub.workshop.registrations.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.unihub.workshop.registrations.application.ports.in.GetMyRegistrationsQuery;
import com.unihub.workshop.registrations.application.ports.out.RegistrationRepository;
import com.unihub.workshop.registrations.domain.model.Registration;
import com.unihub.workshop.registrations.domain.model.RegistrationStatus;
import com.unihub.workshop.registrations.domain.model.RegistrationSummary;
import com.unihub.workshop.shared.exception.ValidationException;
import com.unihub.workshop.shared.pagination.CursorPage;
import com.unihub.workshop.workshops.api.WorkshopApi;
import com.unihub.workshop.workshops.api.dto.WorkshopRegistrationDetailsDto;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GetMyRegistrationsUseCaseImplTest {

    private RegistrationRepository registrationRepository;
    private WorkshopApi workshopApi;
    private GetMyRegistrationsUseCaseImpl useCase;

    private final UUID studentUserId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        registrationRepository = mock(RegistrationRepository.class);
        workshopApi = mock(WorkshopApi.class);
        useCase = new GetMyRegistrationsUseCaseImpl(
            registrationRepository,
            workshopApi
        );
    }

    @Test
    @DisplayName(
        "FR-16, FR-18: Returns enriched registrations with workshop titles and schedule in order"
    )
    void getMyRegistrations_withValidQuery_returnsEnrichedSummaries() {
        UUID workshopId1 = UUID.randomUUID();
        UUID workshopId2 = UUID.randomUUID();
        UUID regId1 = UUID.randomUUID();
        UUID regId2 = UUID.randomUUID();
        Instant now = Instant.now();

        Registration reg1 = Registration.builder()
            .id(regId1)
            .studentUserId(studentUserId)
            .workshopId(workshopId1)
            .status(RegistrationStatus.CONFIRMED)
            .priceAtRegistration(BigDecimal.ZERO)
            .currency("USD")
            .createdAt(now.minusSeconds(100))
            .build();

        Registration reg2 = Registration.builder()
            .id(regId2)
            .studentUserId(studentUserId)
            .workshopId(workshopId2)
            .status(RegistrationStatus.PENDING)
            .priceAtRegistration(BigDecimal.ZERO)
            .currency("USD")
            .createdAt(now.minusSeconds(200))
            .build();

        when(
            registrationRepository.findByStudentUserId(
                eq(studentUserId),
                eq(null),
                eq(null),
                eq(20)
            )
        ).thenReturn(
            new CursorPage<>(List.of(reg1, reg2), "next-cursor-token", true)
        );

        WorkshopRegistrationDetailsDto w1 = new WorkshopRegistrationDetailsDto(
            workshopId1,
            UUID.randomUUID(),
            "Cloud Architecture",
            "cloud-arch",
            "PUBLISHED",
            now.plusSeconds(3600),
            now.plusSeconds(7200),
            BigDecimal.ZERO,
            50,
            "Room 101"
        );
        WorkshopRegistrationDetailsDto w2 = new WorkshopRegistrationDetailsDto(
            workshopId2,
            UUID.randomUUID(),
            "Spring Boot Mastery",
            "spring-boot",
            "PUBLISHED",
            now.plusSeconds(10000),
            now.plusSeconds(14000),
            BigDecimal.ZERO,
            30,
            "Room 202"
        );

        when(
            workshopApi.getWorkshopsForRegistrations(
                Set.of(workshopId1, workshopId2)
            )
        ).thenReturn(Map.of(workshopId1, w1, workshopId2, w2));

        GetMyRegistrationsQuery query = new GetMyRegistrationsQuery(
            studentUserId,
            null,
            null,
            20
        );
        CursorPage<RegistrationSummary> result = useCase.getMyRegistrations(
            query
        );

        assertThat(result.items()).hasSize(2);
        assertThat(result.hasMore()).isTrue();
        assertThat(result.nextCursor()).isEqualTo("next-cursor-token");

        RegistrationSummary item1 = result.items().get(0);
        assertThat(item1.id()).isEqualTo(regId1);
        assertThat(item1.workshopId()).isEqualTo(workshopId1);
        assertThat(item1.workshopTitle()).isEqualTo("Cloud Architecture");
        assertThat(item1.workshopStartsAt()).isEqualTo(w1.startsAt());
        assertThat(item1.status()).isEqualTo(RegistrationStatus.CONFIRMED);

        RegistrationSummary item2 = result.items().get(1);
        assertThat(item2.id()).isEqualTo(regId2);
        assertThat(item2.workshopId()).isEqualTo(workshopId2);
        assertThat(item2.workshopTitle()).isEqualTo("Spring Boot Mastery");
        assertThat(item2.status()).isEqualTo(RegistrationStatus.PENDING);
    }

    @Test
    @DisplayName(
        "Returns empty page when student has no registrations and skips workshop batch lookup"
    )
    void getMyRegistrations_whenEmpty_returnsEmptyPageWithoutCallingWorkshopApi() {
        when(
            registrationRepository.findByStudentUserId(
                eq(studentUserId),
                eq(null),
                eq(null),
                eq(20)
            )
        ).thenReturn(CursorPage.empty());

        GetMyRegistrationsQuery query = new GetMyRegistrationsQuery(
            studentUserId,
            null,
            null,
            null
        );
        CursorPage<RegistrationSummary> result = useCase.getMyRegistrations(
            query
        );

        assertThat(result.items()).isEmpty();
        assertThat(result.hasMore()).isFalse();
        assertThat(result.nextCursor()).isNull();
        verify(workshopApi, never()).getWorkshopsForRegistrations(any());
    }

    @Test
    @DisplayName("FR-17: Correctly parses valid status filter string to enum")
    void getMyRegistrations_withValidStatusFilter_passesEnumToRepository() {
        when(
            registrationRepository.findByStudentUserId(
                eq(studentUserId),
                eq(RegistrationStatus.CONFIRMED),
                eq("cursor1"),
                eq(10)
            )
        ).thenReturn(CursorPage.empty());

        GetMyRegistrationsQuery query = new GetMyRegistrationsQuery(
            studentUserId,
            "CONFIRMED",
            "cursor1",
            10
        );
        useCase.getMyRegistrations(query);

        verify(registrationRepository).findByStudentUserId(
            studentUserId,
            RegistrationStatus.CONFIRMED,
            "cursor1",
            10
        );
    }

    @Test
    @DisplayName(
        "Throws ValidationException 422 when status filter is not a valid enum value"
    )
    void getMyRegistrations_withInvalidStatusFilter_throwsValidationException() {
        GetMyRegistrationsQuery query = new GetMyRegistrationsQuery(
            studentUserId,
            "INVALID_STATUS",
            null,
            20
        );

        assertThatThrownBy(() -> useCase.getMyRegistrations(query))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("Invalid registration status");

        verify(registrationRepository, never()).findByStudentUserId(
            any(),
            any(),
            any(),
            any(Integer.class)
        );
    }

    @Test
    @DisplayName(
        "Handles missing workshop gracefully when not returned by peer WorkshopApi"
    )
    void getMyRegistrations_whenWorkshopNotFoundInBatch_handlesGracefullyWithNullTitle() {
        UUID workshopId = UUID.randomUUID();
        Registration reg = Registration.builder()
            .id(UUID.randomUUID())
            .studentUserId(studentUserId)
            .workshopId(workshopId)
            .status(RegistrationStatus.CONFIRMED)
            .priceAtRegistration(BigDecimal.ZERO)
            .currency("USD")
            .createdAt(Instant.now())
            .build();

        when(
            registrationRepository.findByStudentUserId(
                eq(studentUserId),
                eq(null),
                eq(null),
                eq(20)
            )
        ).thenReturn(new CursorPage<>(List.of(reg), null, false));
        when(
            workshopApi.getWorkshopsForRegistrations(Set.of(workshopId))
        ).thenReturn(Map.of());

        GetMyRegistrationsQuery query = new GetMyRegistrationsQuery(
            studentUserId,
            null,
            null,
            20
        );
        CursorPage<RegistrationSummary> result = useCase.getMyRegistrations(
            query
        );

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).workshopTitle()).isNull();
        assertThat(result.items().get(0).workshopStartsAt()).isNull();
    }
}
