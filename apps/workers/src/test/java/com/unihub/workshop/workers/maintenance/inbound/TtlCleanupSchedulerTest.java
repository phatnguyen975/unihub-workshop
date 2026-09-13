package com.unihub.workshop.workers.maintenance.inbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import com.unihub.workshop.workers.maintenance.core.ports.in.RunTtlCleanup;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.annotation.Scheduled;

@ExtendWith(MockitoExtension.class)
class TtlCleanupSchedulerTest {

    @Mock
    private RunTtlCleanup runTtlCleanup;

    @InjectMocks
    private TtlCleanupScheduler scheduler;

    @Test
    void runTtlCleanup_delegatesToUseCase() {
        scheduler.runTtlCleanup();
        verify(runTtlCleanup).cleanExpiredTokens();
    }

    @Test
    void runTtlCleanup_isAnnotatedWithScheduledCron()
        throws NoSuchMethodException {
        Method method = TtlCleanupScheduler.class.getDeclaredMethod(
            "runTtlCleanup"
        );
        Scheduled scheduled = method.getAnnotation(Scheduled.class);

        assertThat(scheduled).isNotNull();
        assertThat(scheduled.cron()).isEqualTo(
            "${app.ttl-cleanup.cron:0 0 */6 * * *}"
        );
    }
}
