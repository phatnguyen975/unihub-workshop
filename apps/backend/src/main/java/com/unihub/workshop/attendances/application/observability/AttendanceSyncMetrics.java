package com.unihub.workshop.attendances.application.observability;

import com.unihub.workshop.attendances.application.ports.in.SyncAttendancesCommand;
import com.unihub.workshop.attendances.domain.model.AttendanceClassification;
import com.unihub.workshop.attendances.domain.model.AttendanceOutcome;
import com.unihub.workshop.shared.web.RequestLoggingFilter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

/**
 * Observability for the attendance sync flow: Micrometer meters plus the single
 * structured log line emitted per request.
 *
 * <p>The meters are registered on the shared {@link MeterRegistry}, so they are
 * scraped from the existing {@code /actuator/prometheus} endpoint without any new
 * registry or configuration class.
 *
 * <p>The log line deliberately carries only aggregate metadata — the caller, the
 * workshop, the batch size, per-classification tallies, the latency and the trace
 * identifier. Ticket identifiers, QR payloads, signatures and the request body are
 * never passed to this component, so they cannot be logged.
 */
@Slf4j
@Component
public class AttendanceSyncMetrics {

    /** Number of attendance sync requests received. */
    public static final String REQUESTS_TOTAL =
        "attendance.sync.requests.total";

    /** Latency of a completed attendance sync request. */
    public static final String DURATION = "attendance.sync.duration";

    /** Per-entry reconciliation verdicts, tagged by {@link #STATUS_TAG}. */
    public static final String OUTCOMES_TOTAL =
        "attendance.sync.outcomes.total";

    /** Failed bulk writes of the attendance sync flow. */
    public static final String BULK_WRITE_FAILURES_TOTAL =
        "attendance.sync.bulk_write.failures.total";

    /** Tag holding the {@link AttendanceClassification} name of an outcome. */
    public static final String STATUS_TAG = "status";

    private static final String UNKNOWN = "unknown";
    private static final long NANOS_PER_MILLI = 1_000_000L;

    private final MeterRegistry meterRegistry;
    private final Timer requestDuration;

    public AttendanceSyncMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.requestDuration = Timer.builder(DURATION)
            .description("Latency of attendance sync requests")
            .register(meterRegistry);
    }

    /**
     * Counts one received request and starts its latency sample.
     *
     * @param command the request being processed, or {@code null} when it was
     *                rejected before it could be parsed into a command
     * @return the scope to pass to {@link #recordCompleted}
     */
    public RequestScope startRequest(SyncAttendancesCommand command) {
        meterRegistry.counter(REQUESTS_TOTAL).increment();
        return new RequestScope(
            Timer.start(meterRegistry),
            command != null ? command.staffUserId() : null,
            command != null ? command.workshopId() : null,
            entryCount(command)
        );
    }

    /**
     * Counts every freshly produced outcome once, tagged by its classification.
     *
     * <p>Replayed responses are deliberately excluded: their outcomes were already
     * counted when the original request produced them.
     *
     * @param outcomes outcomes produced by this request
     */
    public void recordOutcomes(Collection<AttendanceOutcome> outcomes) {
        if (outcomes == null) {
            return;
        }
        for (AttendanceOutcome outcome : outcomes) {
            meterRegistry
                .counter(
                    OUTCOMES_TOTAL,
                    STATUS_TAG,
                    outcome.classification().name()
                )
                .increment();
        }
    }

    /** Counts one failed bulk write. */
    public void recordBulkWriteFailure() {
        meterRegistry.counter(BULK_WRITE_FAILURES_TOTAL).increment();
    }

    /**
     * Stops the latency sample and writes the single structured log line of the
     * request.
     *
     * @param scope    scope returned by {@link #startRequest}
     * @param outcomes outcomes to tally; empty for a failed or replayed request
     */
    public void recordCompleted(
        RequestScope scope,
        List<AttendanceOutcome> outcomes
    ) {
        long durationNanos = scope.sample().stop(requestDuration);
        Map<AttendanceClassification, Long> counts = tally(outcomes);

        log.info(
            "attendance.sync.completed user_id={} workshopId={} entries={} " +
                "success={} alreadyUsed={} invalidTicket={} durationMs={} traceId={}",
            identifier(scope.userId()),
            identifier(scope.workshopId()),
            scope.entryCount(),
            counts.get(AttendanceClassification.SUCCESS),
            counts.get(AttendanceClassification.ALREADY_USED),
            counts.get(AttendanceClassification.INVALID_TICKET),
            durationNanos / NANOS_PER_MILLI,
            currentTraceId()
        );
    }

    private static int entryCount(SyncAttendancesCommand command) {
        if (command == null || command.entries() == null) {
            return 0;
        }
        return command.entries().size();
    }

    private static Map<AttendanceClassification, Long> tally(
        Collection<AttendanceOutcome> outcomes
    ) {
        Map<AttendanceClassification, Long> counts = new EnumMap<>(
            AttendanceClassification.class
        );
        for (AttendanceClassification classification : AttendanceClassification.values()) {
            counts.put(classification, 0L);
        }
        if (outcomes != null) {
            for (AttendanceOutcome outcome : outcomes) {
                counts.merge(outcome.classification(), 1L, Long::sum);
            }
        }
        return counts;
    }

    private static String identifier(UUID value) {
        return value != null ? value.toString() : UNKNOWN;
    }

    private static String currentTraceId() {
        String traceId = MDC.get(RequestLoggingFilter.MDC_TRACE_ID);
        return traceId != null ? traceId : UNKNOWN;
    }

    /**
     * Metadata captured when a request starts, needed to close it out.
     *
     * @param sample     latency sample of the request
     * @param userId     authenticated staff member, or {@code null}
     * @param workshopId workshop being synced, or {@code null}
     * @param entryCount number of submitted entries
     */
    public record RequestScope(
        Timer.Sample sample,
        UUID userId,
        UUID workshopId,
        int entryCount
    ) {}
}
