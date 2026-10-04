package com.unihub.workshop.workshops.infrastructure.scheduler;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.unihub.workshop.workshops.application.ports.in.ReconcileWorkshopCapacityUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("WorkshopCapacityReconciliationScheduler Unit Tests")
class WorkshopCapacityReconciliationSchedulerTest {

    @Mock
    private ReconcileWorkshopCapacityUseCase reconcileWorkshopCapacityUseCase;

    private WorkshopCapacityReconciliationScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new WorkshopCapacityReconciliationScheduler(
            reconcileWorkshopCapacityUseCase
        );
    }

    @Test
    @DisplayName("Scheduled run invokes reconcileWorkshopCapacityUseCase")
    void run_invokesUseCase() {
        scheduler.run();

        verify(reconcileWorkshopCapacityUseCase).reconcileCapacity();
    }

    @Test
    @DisplayName(
        "Scheduled run catches exception from use case and does not rethrow"
    )
    void run_useCaseThrows_catchesAndDoesNotThrow() {
        doThrow(new RuntimeException("Reconciliation error"))
            .when(reconcileWorkshopCapacityUseCase)
            .reconcileCapacity();

        assertThatCode(() -> scheduler.run()).doesNotThrowAnyException();
        verify(reconcileWorkshopCapacityUseCase).reconcileCapacity();
    }
}
