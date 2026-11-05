package com.unihub.workshop.workshops.application.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.unihub.workshop.workshops.api.dto.WorkshopRegistrationDetailsDto;
import com.unihub.workshop.workshops.application.ports.out.WorkshopAssignmentRepository;
import com.unihub.workshop.workshops.application.ports.out.WorkshopRepository;
import com.unihub.workshop.workshops.domain.model.Workshop;
import com.unihub.workshop.workshops.domain.model.WorkshopStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WorkshopFacadeTest {

    private WorkshopRepository workshopRepository;
    private WorkshopAssignmentRepository workshopAssignmentRepository;
    private WorkshopFacade workshopFacade;

    @BeforeEach
    void setUp() {
        workshopRepository = mock(WorkshopRepository.class);
        workshopAssignmentRepository = mock(WorkshopAssignmentRepository.class);
        workshopFacade = new WorkshopFacade(
            workshopRepository,
            workshopAssignmentRepository
        );
    }

    @Test
    @DisplayName(
        "getWorkshopForRegistration: returns empty when workshopId is null"
    )
    void getWorkshopForRegistration_NullWorkshopId_ReturnsEmpty() {
        Optional<WorkshopRegistrationDetailsDto> result =
            workshopFacade.getWorkshopForRegistration(null);

        assertThat(result).isEmpty();
        verifyNoInteractions(workshopRepository);
    }

    @Test
    @DisplayName(
        "getWorkshopForRegistration: returns empty when workshop is not found"
    )
    void getWorkshopForRegistration_NotFound_ReturnsEmpty() {
        UUID workshopId = UUID.randomUUID();
        when(workshopRepository.findById(workshopId)).thenReturn(
            Optional.empty()
        );

        Optional<WorkshopRegistrationDetailsDto> result =
            workshopFacade.getWorkshopForRegistration(workshopId);

        assertThat(result).isEmpty();
        verify(workshopRepository).findById(workshopId);
    }

    @Test
    @DisplayName(
        "getWorkshopForRegistration: returns WorkshopRegistrationDetailsDto when workshop exists"
    )
    void getWorkshopForRegistration_Found_ReturnsDetailsDto() {
        UUID workshopId = UUID.randomUUID();
        UUID organizationId = UUID.randomUUID();
        Instant startsAt = Instant.now().plusSeconds(3600);
        Instant endsAt = startsAt.plusSeconds(7200);

        Workshop workshop = Workshop.builder()
            .id(workshopId)
            .organizationId(organizationId)
            .title("Spring Boot Deep Dive")
            .slug("spring-boot-deep-dive")
            .status(WorkshopStatus.PUBLISHED)
            .startsAt(startsAt)
            .endsAt(endsAt)
            .price(BigDecimal.ZERO)
            .capacity(50)
            .location("Hall A")
            .build();

        when(workshopRepository.findById(workshopId)).thenReturn(
            Optional.of(workshop)
        );

        Optional<WorkshopRegistrationDetailsDto> result =
            workshopFacade.getWorkshopForRegistration(workshopId);

        assertThat(result).isPresent();
        WorkshopRegistrationDetailsDto dto = result.get();
        assertThat(dto.id()).isEqualTo(workshopId);
        assertThat(dto.organizationId()).isEqualTo(organizationId);
        assertThat(dto.title()).isEqualTo("Spring Boot Deep Dive");
        assertThat(dto.slug()).isEqualTo("spring-boot-deep-dive");
        assertThat(dto.status()).isEqualTo("PUBLISHED");
        assertThat(dto.startsAt()).isEqualTo(startsAt);
        assertThat(dto.endsAt()).isEqualTo(endsAt);
        assertThat(dto.price()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(dto.capacity()).isEqualTo(50);
        assertThat(dto.location()).isEqualTo("Hall A");
    }

    @Test
    @DisplayName(
        "getWorkshopsForRegistrations: returns empty map when collection is null or empty"
    )
    void getWorkshopsForRegistrations_NullOrEmpty_ReturnsEmptyMap() {
        Map<UUID, WorkshopRegistrationDetailsDto> resultNull =
            workshopFacade.getWorkshopsForRegistrations(null);
        Map<UUID, WorkshopRegistrationDetailsDto> resultEmpty =
            workshopFacade.getWorkshopsForRegistrations(
                Collections.emptyList()
            );

        assertThat(resultNull).isEmpty();
        assertThat(resultEmpty).isEmpty();
        verifyNoInteractions(workshopRepository);
    }

    @Test
    @DisplayName(
        "getWorkshopsForRegistrations: resolves multiple workshops in batch"
    )
    void getWorkshopsForRegistrations_MultipleIds_ResolvesBatch() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();

        Workshop w1 = Workshop.builder()
            .id(id1)
            .title("Workshop One")
            .slug("workshop-one")
            .status(WorkshopStatus.PUBLISHED)
            .startsAt(Instant.now().plusSeconds(3600))
            .endsAt(Instant.now().plusSeconds(7200))
            .price(BigDecimal.ZERO)
            .capacity(30)
            .location("Room 101")
            .build();

        Workshop w2 = Workshop.builder()
            .id(id2)
            .title("Workshop Two")
            .slug("workshop-two")
            .status(WorkshopStatus.PUBLISHED)
            .startsAt(Instant.now().plusSeconds(10000))
            .endsAt(Instant.now().plusSeconds(14000))
            .price(new BigDecimal("25.00"))
            .capacity(40)
            .location("Room 102")
            .build();

        when(workshopRepository.findAllById(List.of(id1, id2))).thenReturn(
            List.of(w1, w2)
        );

        Map<UUID, WorkshopRegistrationDetailsDto> result =
            workshopFacade.getWorkshopsForRegistrations(List.of(id1, id2));

        assertThat(result).hasSize(2);
        assertThat(result.get(id1).title()).isEqualTo("Workshop One");
        assertThat(result.get(id2).title()).isEqualTo("Workshop Two");
        assertThat(result.get(id1).status()).isEqualTo("PUBLISHED");
        assertThat(result.get(id2).price()).isEqualByComparingTo(
            new BigDecimal("25.00")
        );
    }

    @Test
    @DisplayName(
        "isStaffAssignedToWorkshop: returns false when either identifier is null"
    )
    void isStaffAssignedToWorkshop_NullIdentifier_ReturnsFalse() {
        UUID workshopId = UUID.randomUUID();
        UUID staffUserId = UUID.randomUUID();

        assertThat(
            workshopFacade.isStaffAssignedToWorkshop(null, staffUserId)
        ).isFalse();
        assertThat(
            workshopFacade.isStaffAssignedToWorkshop(workshopId, null)
        ).isFalse();

        verifyNoInteractions(workshopAssignmentRepository);
    }

    @Test
    @DisplayName(
        "isStaffAssignedToWorkshop: delegates the assignment existence check to the repository"
    )
    void isStaffAssignedToWorkshop_delegatesToAssignmentRepository() {
        UUID workshopId = UUID.randomUUID();
        UUID assignedStaffUserId = UUID.randomUUID();
        UUID unassignedStaffUserId = UUID.randomUUID();

        when(
            workshopAssignmentRepository.existsByWorkshopIdAndStaffUserId(
                workshopId,
                assignedStaffUserId
            )
        ).thenReturn(true);
        when(
            workshopAssignmentRepository.existsByWorkshopIdAndStaffUserId(
                workshopId,
                unassignedStaffUserId
            )
        ).thenReturn(false);

        assertThat(
            workshopFacade.isStaffAssignedToWorkshop(
                workshopId,
                assignedStaffUserId
            )
        ).isTrue();
        assertThat(
            workshopFacade.isStaffAssignedToWorkshop(
                workshopId,
                unassignedStaffUserId
            )
        ).isFalse();

        verify(workshopAssignmentRepository).existsByWorkshopIdAndStaffUserId(
            workshopId,
            assignedStaffUserId
        );
        verify(workshopAssignmentRepository).existsByWorkshopIdAndStaffUserId(
            workshopId,
            unassignedStaffUserId
        );
    }
}
