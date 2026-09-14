package com.unihub.workshop.shared.realtime.application.ports.out;

/**
 * Outbound port for recording real-time SSE metrics without coupling application
 * logic to infrastructure adapters.
 */
public interface RealtimeMetricsPort {
    /**
     * Increments the counter for pushed real-time events.
     *
     * @param eventType the type of event pushed
     */
    void incrementEventsPushed(String eventType);

    /**
     * Increments the counter for disconnected SSE streams.
     *
     * @param reason the reason for disconnection (e.g. "complete", "timeout", "error")
     */
    void incrementDisconnects(String reason);
}
