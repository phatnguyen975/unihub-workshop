package com.unihub.workshop.attendances.application.observability;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.unihub.workshop.attendances.application.ports.in.SyncAttendancesCommand;
import com.unihub.workshop.attendances.domain.model.AttendanceClassification;
import com.unihub.workshop.attendances.domain.model.AttendanceOutcome;
import com.unihub.workshop.attendances.domain.model.SyncEntry;
import com.unihub.workshop.shared.web.RequestLoggingFilter;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

class AttendanceSyncMetricsTest {

    private static final UUID WORKSHOP_ID = UUID.randomUUID();
    private static final UUID STAFF_ID = UUID.randomUUID();
    private static final String IDEMPOTENCY_KEY = UUID.randomUUID().toString();
    private static final String DEVICE_ID = "device-abc-123";
    private static final String TRACE_ID = "trace-abc-123";

    private MeterRegistry meterRegistry;
    private AttendanceSyncMetrics metrics;
    private Logger metricsLogger;
    private ListAppender<ILoggingEvent> logAppender;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        metrics = new AttendanceSyncMetrics(meterRegistry);
        metricsLogger = (Logger) LoggerFactory.getLogger(
            AttendanceSyncMetrics.class
        );
        logAppender = new ListAppender<>();
        logAppender.start();
        metricsLogger.addAppender(logAppender);
    }

    @AfterEach
    void tearDown() {
        metricsLogger.detachAppender(logAppender);
        logAppender.stop();
        MDC.remove(RequestLoggingFilter.MDC_TRACE_ID);
    }

    private static SyncAttendancesCommand command(int entryCount) {
        List<SyncEntry> entries = IntStream.range(0, entryCount)
            .mapToObj(index ->
                new SyncEntry(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    Instant.parse("2026-11-15T09:30:00Z")
                )
            )
            .toList();
        return new SyncAttendancesCommand(
            WORKSHOP_ID,
            STAFF_ID,
            IDEMPOTENCY_KEY,
            DEVICE_ID,
            entries
        );
    }

    private static AttendanceOutcome outcome(AttendanceClassification status) {
        return new AttendanceOutcome(UUID.randomUUID(), status);
    }

    private Counter outcomeCounter(String status) {
        return meterRegistry
            .get(AttendanceSyncMetrics.OUTCOMES_TOTAL)
            .tag(AttendanceSyncMetrics.STATUS_TAG, status)
            .counter();
    }

    @Test
    @DisplayName("starting a request increments attendance.sync.requests.total")
    void startRequest_incrementsRequestsTotal() {
        metrics.startRequest(command(3));

        assertThat(
            meterRegistry
                .get(AttendanceSyncMetrics.REQUESTS_TOTAL)
                .counter()
                .count()
        ).isEqualTo(1.0);
    }

    @Test
    @DisplayName(
        "completing a request records one sample in attendance.sync.duration"
    )
    void recordCompleted_recordsLatency() {
        AttendanceSyncMetrics.RequestScope scope = metrics.startRequest(
            command(2)
        );

        metrics.recordCompleted(scope, List.of());

        Timer timer = meterRegistry
            .find(AttendanceSyncMetrics.DURATION)
            .timer();
        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1);
    }

    @Test
    @DisplayName(
        "every outcome increments attendance.sync.outcomes.total once with its status tag"
    )
    void recordOutcomes_incrementsOncePerOutcomeWithStatusTag() {
        metrics.recordOutcomes(
            List.of(
                outcome(AttendanceClassification.SUCCESS),
                outcome(AttendanceClassification.SUCCESS),
                outcome(AttendanceClassification.ALREADY_USED),
                outcome(AttendanceClassification.INVALID_TICKET)
            )
        );

        assertThat(outcomeCounter("SUCCESS").count()).isEqualTo(2.0);
        assertThat(outcomeCounter("ALREADY_USED").count()).isEqualTo(1.0);
        assertThat(outcomeCounter("INVALID_TICKET").count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("no outcome counter is created when there is no outcome")
    void recordOutcomes_emptyBatch_createsNoCounter() {
        metrics.recordOutcomes(List.of());

        assertThat(
            meterRegistry.find(AttendanceSyncMetrics.OUTCOMES_TOTAL).counters()
        ).isEmpty();
    }

    @Test
    @DisplayName(
        "a bulk write failure increments attendance.sync.bulk_write.failures.total"
    )
    void recordBulkWriteFailure_incrementsFailureCounter() {
        metrics.recordBulkWriteFailure();
        metrics.recordBulkWriteFailure();

        assertThat(
            meterRegistry
                .get(AttendanceSyncMetrics.BULK_WRITE_FAILURES_TOTAL)
                .counter()
                .count()
        ).isEqualTo(2.0);
    }

    @Test
    @DisplayName(
        "completing a request writes exactly one structured log line with the required fields"
    )
    void recordCompleted_logsExactlyOneLineWithRequiredFields() {
        AttendanceSyncMetrics.RequestScope scope = metrics.startRequest(
            command(3)
        );

        metrics.recordCompleted(
            scope,
            List.of(
                outcome(AttendanceClassification.SUCCESS),
                outcome(AttendanceClassification.SUCCESS),
                outcome(AttendanceClassification.ALREADY_USED)
            )
        );

        assertThat(logAppender.list).hasSize(1);
        String message = logAppender.list.get(0).getFormattedMessage();
        assertThat(message).contains("user_id=" + STAFF_ID);
        assertThat(message).contains("workshopId=" + WORKSHOP_ID);
        assertThat(message).contains("entries=3");
        assertThat(message).contains("success=2");
        assertThat(message).contains("alreadyUsed=1");
        assertThat(message).contains("invalidTicket=0");
        assertThat(message).contains("durationMs=");
        assertThat(message).contains("traceId=");
    }

    @Test
    @DisplayName("the log line carries the current trace identifier from MDC")
    void recordCompleted_logsCurrentTraceId() {
        MDC.put(RequestLoggingFilter.MDC_TRACE_ID, TRACE_ID);
        AttendanceSyncMetrics.RequestScope scope = metrics.startRequest(
            command(1)
        );

        metrics.recordCompleted(scope, List.of());

        assertThat(logAppender.list).hasSize(1);
        assertThat(logAppender.list.get(0).getFormattedMessage()).contains(
            "traceId=" + TRACE_ID
        );
    }

    @Test
    @DisplayName(
        "the log line never contains ticket identifiers, QR payloads or the request body"
    )
    void recordCompleted_neverLogsTicketData() {
        UUID syncId = UUID.randomUUID();
        AttendanceSyncMetrics.RequestScope scope = metrics.startRequest(
            command(1)
        );

        metrics.recordCompleted(
            scope,
            List.of(
                new AttendanceOutcome(syncId, AttendanceClassification.SUCCESS)
            )
        );

        String message = logAppender.list.get(0).getFormattedMessage();
        assertThat(message).doesNotContain(syncId.toString());
        assertThat(message).doesNotContain(DEVICE_ID);
        assertThat(message).doesNotContain(IDEMPOTENCY_KEY);
    }

    @Test
    @DisplayName(
        "an absent command is still counted and logged without a user or workshop identifier"
    )
    void startRequest_nullCommand_logsUnknownContext() {
        AttendanceSyncMetrics.RequestScope scope = metrics.startRequest(null);

        metrics.recordCompleted(scope, List.of());

        assertThat(
            meterRegistry
                .get(AttendanceSyncMetrics.REQUESTS_TOTAL)
                .counter()
                .count()
        ).isEqualTo(1.0);
        assertThat(logAppender.list).hasSize(1);
        String message = logAppender.list.get(0).getFormattedMessage();
        assertThat(message).contains("user_id=unknown");
        assertThat(message).contains("workshopId=unknown");
        assertThat(message).contains("entries=0");
    }
}
