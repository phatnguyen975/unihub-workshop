package com.unihub.workshop.registrations.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.unihub.workshop.iam.api.IamApi;
import com.unihub.workshop.iam.api.dto.StudentProfileDto;
import com.unihub.workshop.registrations.application.ports.in.GetWorkshopAttendeeRosterQuery;
import com.unihub.workshop.registrations.application.ports.out.RegistrationRepository;
import com.unihub.workshop.registrations.domain.model.Registration;
import com.unihub.workshop.registrations.domain.model.RegistrationStatus;
import com.unihub.workshop.registrations.domain.model.WorkshopAttendeeRosterItem;
import com.unihub.workshop.shared.exception.ForbiddenException;
import com.unihub.workshop.shared.exception.NotFoundException;
import com.unihub.workshop.shared.exception.ValidationException;
import com.unihub.workshop.shared.pagination.CursorPage;
import com.unihub.workshop.workshops.api.WorkshopApi;
import com.unihub.workshop.workshops.api.dto.WorkshopRegistrationDetailsDto;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GetWorkshopAttendeeRosterUseCaseTest {

    @Mock
    private RegistrationRepository registrationRepository;

    @Mock
    private WorkshopApi workshopApi;

    @Mock
    private IamApi iamApi;

    @InjectMocks
    private GetWorkshopAttendeeRosterUseCaseImpl useCase;

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
        UUID organizationId
    ) {
        return new WorkshopRegistrationDetailsDto(
            id,
            organizationId,
            "Test Workshop",
            "test-workshop",
            "PUBLISHED",
            now,
            now.plusSeconds(7200),
            BigDecimal.ZERO,
            100,
            "Room 101"
        );
    }

    private Registration createMockRegistration(
        UUID id,
        UUID workshopId,
        UUID studentId,
        RegistrationStatus status
    ) {
        return Registration.builder()
            .id(id)
            .workshopId(workshopId)
            .studentUserId(studentId)
            .status(status)
            .priceAtRegistration(BigDecimal.ZERO)
            .currency("USD")
            .idempotencyKeyId(UUID.randomUUID())
            .createdAt(now)
            .updatedAt(now)
            .createdBy(studentId)
            .updatedBy(studentId)
            .build();
    }

    @Test
    @DisplayName(
        "Scenario 8: Happy path returns cursor page of roster items with batch enriched student profiles (FR-10, 11, 13, 14)"
    )
    void shouldReturnRoster_HappyPath_FirstPage() {
        // Arrange
        GetWorkshopAttendeeRosterQuery query =
            new GetWorkshopAttendeeRosterQuery(
                workshopId,
                orgId,
                null,
                null,
                20
            );
        WorkshopRegistrationDetailsDto workshop = createWorkshopDto(
            workshopId,
            orgId
        );

        UUID regId = UUID.randomUUID();
        UUID studentUserId = UUID.randomUUID();
        Registration registration = createMockRegistration(
            regId,
            workshopId,
            studentUserId,
            RegistrationStatus.CONFIRMED
        );
        CursorPage<Registration> registrationPage = new CursorPage<>(
            List.of(registration),
            "next-token-123",
            true
        );

        StudentProfileDto studentProfile = new StudentProfileDto(
            studentUserId,
            UUID.randomUUID(),
            "Nguyen Van A",
            "23123456@student.hcmus.edu.vn",
            "23123456"
        );

        when(workshopApi.getWorkshopForRegistration(workshopId)).thenReturn(
            Optional.of(workshop)
        );
        when(
            registrationRepository.findByWorkshopId(workshopId, null, null, 20)
        ).thenReturn(registrationPage);
        when(iamApi.getStudentProfilesByIds(Set.of(studentUserId))).thenReturn(
            Map.of(studentUserId, studentProfile)
        );

        // Act
        CursorPage<WorkshopAttendeeRosterItem> result = useCase.getRoster(
            query
        );

        // Assert
        assertThat(result.items()).hasSize(1);
        assertThat(result.nextCursor()).isEqualTo("next-token-123");
        assertThat(result.hasMore()).isTrue();

        WorkshopAttendeeRosterItem item = result.items().get(0);
        assertThat(item.id()).isEqualTo(regId);
        assertThat(item.status()).isEqualTo(RegistrationStatus.CONFIRMED);
        assertThat(item.price()).isEqualTo(BigDecimal.ZERO);
        assertThat(item.currency()).isEqualTo("USD");
        assertThat(item.registeredAt()).isEqualTo(now);

        assertThat(item.student().id()).isEqualTo(studentUserId);
        assertThat(item.student().fullName()).isEqualTo("Nguyen Van A");
        assertThat(item.student().studentNumber()).isEqualTo("23123456");
        assertThat(item.student().email()).isEqualTo(
            "23123456@student.hcmus.edu.vn"
        );

        verify(iamApi).getStudentProfilesByIds(Set.of(studentUserId));
    }

    @Test
    @DisplayName(
        "Scenario 9: Second page using cursor returns remaining items with nextCursor null and hasMore false"
    )
    void shouldReturnSecondPage_whenCursorProvided() {
        // Arrange
        String cursor = "cursor-page-1";
        GetWorkshopAttendeeRosterQuery query =
            new GetWorkshopAttendeeRosterQuery(
                workshopId,
                orgId,
                null,
                cursor,
                20
            );
        WorkshopRegistrationDetailsDto workshop = createWorkshopDto(
            workshopId,
            orgId
        );

        UUID regId = UUID.randomUUID();
        UUID studentUserId = UUID.randomUUID();
        Registration registration = createMockRegistration(
            regId,
            workshopId,
            studentUserId,
            RegistrationStatus.CONFIRMED
        );
        CursorPage<Registration> registrationPage = new CursorPage<>(
            List.of(registration),
            null,
            false
        );

        when(workshopApi.getWorkshopForRegistration(workshopId)).thenReturn(
            Optional.of(workshop)
        );
        when(
            registrationRepository.findByWorkshopId(
                workshopId,
                null,
                cursor,
                20
            )
        ).thenReturn(registrationPage);
        when(iamApi.getStudentProfilesByIds(Set.of(studentUserId))).thenReturn(
            Map.of()
        );

        // Act
        CursorPage<WorkshopAttendeeRosterItem> result = useCase.getRoster(
            query
        );

        // Assert
        assertThat(result.items()).hasSize(1);
        assertThat(result.nextCursor()).isNull();
        assertThat(result.hasMore()).isFalse();
    }

    @Test
    @DisplayName(
        "Scenario 10: Filters by status CONFIRMED when status query param provided (FR-12)"
    )
    void shouldFilterByStatusConfirmed() {
        // Arrange
        GetWorkshopAttendeeRosterQuery query =
            new GetWorkshopAttendeeRosterQuery(
                workshopId,
                orgId,
                "CONFIRMED",
                null,
                20
            );
        WorkshopRegistrationDetailsDto workshop = createWorkshopDto(
            workshopId,
            orgId
        );
        CursorPage<Registration> emptyPage = new CursorPage<>(
            List.of(),
            null,
            false
        );

        when(workshopApi.getWorkshopForRegistration(workshopId)).thenReturn(
            Optional.of(workshop)
        );
        when(
            registrationRepository.findByWorkshopId(
                workshopId,
                RegistrationStatus.CONFIRMED,
                null,
                20
            )
        ).thenReturn(emptyPage);

        // Act
        CursorPage<WorkshopAttendeeRosterItem> result = useCase.getRoster(
            query
        );

        // Assert
        assertThat(result.items()).isEmpty();
        verify(registrationRepository).findByWorkshopId(
            workshopId,
            RegistrationStatus.CONFIRMED,
            null,
            20
        );
    }

    @Test
    @DisplayName(
        "Scenario 11: Rejects size > 100 with ValidationException (FR-13, BR-04)"
    )
    void shouldThrowValidationException_whenSizeExceeds100() {
        GetWorkshopAttendeeRosterQuery query =
            new GetWorkshopAttendeeRosterQuery(
                workshopId,
                orgId,
                null,
                null,
                101
            );

        assertThatThrownBy(() -> useCase.getRoster(query))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("size must be between 1 and 100");

        verifyNoInteractions(workshopApi);
        verifyNoInteractions(registrationRepository);
    }

    @Test
    @DisplayName("Scenario 11: Rejects size < 1 with ValidationException")
    void shouldThrowValidationException_whenSizeLessThan1() {
        GetWorkshopAttendeeRosterQuery query =
            new GetWorkshopAttendeeRosterQuery(
                workshopId,
                orgId,
                null,
                null,
                0
            );

        assertThatThrownBy(() -> useCase.getRoster(query))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("size must be between 1 and 100");
    }

    @Test
    @DisplayName(
        "Scenario 15: Rejects invalid status enum value with ValidationException (FR-12)"
    )
    void shouldThrowValidationException_whenInvalidStatusProvided() {
        GetWorkshopAttendeeRosterQuery query =
            new GetWorkshopAttendeeRosterQuery(
                workshopId,
                orgId,
                "INVALID_STATUS",
                null,
                20
            );

        assertThatThrownBy(() -> useCase.getRoster(query))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("Invalid registration status");
    }

    @Test
    @DisplayName(
        "Scenario 4: Throws NotFoundException when workshop does not exist (FR-02)"
    )
    void shouldThrowNotFoundException_whenWorkshopDoesNotExist() {
        GetWorkshopAttendeeRosterQuery query =
            new GetWorkshopAttendeeRosterQuery(
                workshopId,
                orgId,
                null,
                null,
                20
            );
        when(workshopApi.getWorkshopForRegistration(workshopId)).thenReturn(
            Optional.empty()
        );

        assertThatThrownBy(() -> useCase.getRoster(query))
            .isInstanceOf(NotFoundException.class)
            .hasMessageContaining("Workshop not found");
    }

    @Test
    @DisplayName(
        "Scenario 5: Throws ForbiddenException when caller org does not match workshop org (FR-03, BR-01)"
    )
    void shouldThrowForbiddenException_whenOrgMismatch() {
        UUID differentOrgId = UUID.randomUUID();
        GetWorkshopAttendeeRosterQuery query =
            new GetWorkshopAttendeeRosterQuery(
                workshopId,
                orgId,
                null,
                null,
                20
            );
        WorkshopRegistrationDetailsDto workshop = createWorkshopDto(
            workshopId,
            differentOrgId
        );

        when(workshopApi.getWorkshopForRegistration(workshopId)).thenReturn(
            Optional.of(workshop)
        );

        assertThatThrownBy(() -> useCase.getRoster(query))
            .isInstanceOf(ForbiddenException.class)
            .hasMessageContaining("Forbidden");
    }
}
