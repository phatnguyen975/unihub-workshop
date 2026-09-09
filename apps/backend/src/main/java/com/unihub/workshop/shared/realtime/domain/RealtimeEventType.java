package com.unihub.workshop.shared.realtime.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Enumeration of all platform event types supported by the Unified Event Catalog.
 */
public enum RealtimeEventType {
    NOTIFICATION_NEW("notification.new"),
    NOTIFICATION_READ("notification.read"),
    AI_SUMMARY_COMPLETED("ai.summary.completed"),
    AI_SUMMARY_FAILED("ai.summary.failed"),
    ATTENDANCE_UPDATED("attendance.updated"),
    REGISTRATION_UPDATED("registration.updated"),
    PAYMENT_CONFIRMED("payment.confirmed");

    private final String value;

    RealtimeEventType(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static RealtimeEventType fromValue(String value) {
        if (value == null) {
            return null;
        }
        for (RealtimeEventType type : values()) {
            if (type.value.equalsIgnoreCase(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown realtime event type: " + value);
    }
}
