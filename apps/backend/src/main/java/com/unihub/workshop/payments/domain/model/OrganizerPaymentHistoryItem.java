package com.unihub.workshop.payments.domain.model;

import java.util.UUID;

/**
 * Read model combining one payment with the registration context and the minimal student
 * identity an organizer needs for operational reconciliation.
 *
 * @param payment the payment record owned by this module
 * @param workshopId workshop targeted by the linked registration, or null when unavailable
 * @param registrationStatus current registration status name, or null when unavailable
 * @param studentFullName student name resolved through the IAM facade, or null when unavailable
 * @param studentNumber institution student number, or null when unavailable
 */
public record OrganizerPaymentHistoryItem(
    Payment payment,
    UUID workshopId,
    String registrationStatus,
    String studentFullName,
    String studentNumber
) {}
