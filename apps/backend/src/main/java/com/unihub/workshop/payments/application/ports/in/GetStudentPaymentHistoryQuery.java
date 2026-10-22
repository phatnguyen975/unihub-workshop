package com.unihub.workshop.payments.application.ports.in;

import java.util.UUID;

/**
 * Query describing one page of an authenticated student's payment history.
 *
 * <p>{@code size} is carried as the raw request value so the application layer owns its
 * conversion and can reject an invalid value with the contract's validation error instead of
 * letting a framework-level parsing failure surface as a different status code.
 *
 * @param studentUserId authenticated student identifier (mandatory)
 * @param status optional payment status filter, matched case-insensitively
 * @param cursor optional opaque keyset cursor issued for the same filter set
 * @param size optional requested page size, as supplied by the caller
 */
public record GetStudentPaymentHistoryQuery(
    UUID studentUserId,
    String status,
    String cursor,
    String size
) {
    public GetStudentPaymentHistoryQuery {
        if (studentUserId == null) {
            throw new IllegalArgumentException(
                "studentUserId must not be null"
            );
        }
    }
}
