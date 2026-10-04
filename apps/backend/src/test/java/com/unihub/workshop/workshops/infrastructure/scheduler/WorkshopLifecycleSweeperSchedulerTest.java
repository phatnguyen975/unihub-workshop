package com.unihub.workshop.workshops.infrastructure.scheduler;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.unihub.workshop.workshops.application.ports.in.SweepWorkshopLifecycleUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("WorkshopLifecycleSweeperScheduler Unit Tests")
class WorkshopLifecycleSweeperSchedulerTest {

    @Mock
    private SweepWorkshopLifecycleUseCase sweepWorkshopLifecycleUseCase;

    private WorkshopLifecycleSweeperScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new WorkshopLifecycleSweeperScheduler(
            sweepWorkshopLifecycleUseCase
        );
    }

    @Test
    @DisplayName("Scheduled run invokes sweepWorkshopLifecycleUseCase")
    void run_invokesUseCase() {
        scheduler.run();

        verify(sweepWorkshopLifecycleUseCase).sweepLifecycle();
    }

    @Test
    @DisplayName(
        "Scheduled run catches exception from use case and does not rethrow"
    )
    void run_useCaseThrows_catchesAndDoesNotThrow() {
        doThrow(new RuntimeException("Database error"))
            .when(sweepWorkshopLifecycleUseCase)
            .sweepLifecycle();

        assertThatCode(() -> scheduler.run()).doesNotThrowAnyException();
        verify(sweepWorkshopLifecycleUseCase).sweepLifecycle();
    }
}
