package com.unihub.workshop.workers.maintenance.core.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.unihub.workshop.workers.maintenance.core.ports.out.TtlCleanupPort;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(MockitoExtension.class)
class TtlCleanupServiceTest {

    @Mock
    private TtlCleanupPort ttlCleanupPort;

    @InjectMocks
    private TtlCleanupService service;

    @Test
    void cleanExpiredTokens_when1200ExpiredRowsInOneTable_batchesIntoThreeDeletes() {
        when(
            ttlCleanupPort.purgeExpiredRows(eq("idempotency_keys"), eq(500))
        ).thenReturn(500, 500, 200);
        when(
            ttlCleanupPort.purgeExpiredRows(eq("refresh_tokens"), eq(500))
        ).thenReturn(0);
        when(
            ttlCleanupPort.purgeExpiredRows(
                eq("user_verification_tokens"),
                eq(500)
            )
        ).thenReturn(0);

        service.cleanExpiredTokens();

        verify(ttlCleanupPort, times(3)).purgeExpiredRows(
            eq("idempotency_keys"),
            eq(500)
        );
        verify(ttlCleanupPort, times(1)).purgeExpiredRows(
            eq("refresh_tokens"),
            eq(500)
        );
        verify(ttlCleanupPort, times(1)).purgeExpiredRows(
            eq("user_verification_tokens"),
            eq(500)
        );
    }

    @Test
    void cleanExpiredTokens_when0ExpiredRows_callsPurgeOncePerTable() {
        when(
            ttlCleanupPort.purgeExpiredRows(eq("idempotency_keys"), eq(500))
        ).thenReturn(0);
        when(
            ttlCleanupPort.purgeExpiredRows(eq("refresh_tokens"), eq(500))
        ).thenReturn(0);
        when(
            ttlCleanupPort.purgeExpiredRows(
                eq("user_verification_tokens"),
                eq(500)
            )
        ).thenReturn(0);

        service.cleanExpiredTokens();

        verify(ttlCleanupPort, times(1)).purgeExpiredRows(
            eq("idempotency_keys"),
            eq(500)
        );
        verify(ttlCleanupPort, times(1)).purgeExpiredRows(
            eq("refresh_tokens"),
            eq(500)
        );
        verify(ttlCleanupPort, times(1)).purgeExpiredRows(
            eq("user_verification_tokens"),
            eq(500)
        );
    }

    @Test
    void cleanExpiredTokens_isAnnotatedWithTransactional()
        throws NoSuchMethodException {
        Method method = TtlCleanupService.class.getDeclaredMethod(
            "cleanExpiredTokens"
        );
        Transactional transactional = method.getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
        assertThat(transactional.readOnly()).isFalse();
    }
}
