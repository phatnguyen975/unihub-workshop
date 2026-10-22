package com.unihub.workshop.payments.application.usecases;

import com.unihub.workshop.payments.domain.model.PaymentStatus;
import com.unihub.workshop.shared.exception.ValidationException;
import java.util.Locale;

/**
 * Parses and validates the pagination inputs shared by every payment-history query.
 *
 * <p>Request values stay raw until this layer so an unsupported value is reported with the
 * contract's validation error rather than a framework-specific parsing failure.
 */
final class PaymentHistoryQueryParser {

    static final int DEFAULT_SIZE = 20;
    static final int MAX_SIZE = 100;

    private static final String SIZE_FIELD = "size";
    private static final String STATUS_FIELD = "status";

    private PaymentHistoryQueryParser() {}

    static PaymentStatus parseStatus(String statusStr) {
        if (statusStr == null || statusStr.isBlank()) {
            return null;
        }
        try {
            return PaymentStatus.valueOf(
                statusStr.trim().toUpperCase(Locale.ROOT)
            );
        } catch (IllegalArgumentException e) {
            throw new ValidationException(
                STATUS_FIELD,
                "Invalid payment status: " + statusStr
            );
        }
    }

    static int parseSize(String sizeStr) {
        if (sizeStr == null) {
            return DEFAULT_SIZE;
        }
        int size;
        try {
            size = Integer.parseInt(sizeStr.trim());
        } catch (NumberFormatException e) {
            throw new ValidationException(
                SIZE_FIELD,
                "size must be an integer between 1 and " + MAX_SIZE
            );
        }
        if (size < 1 || size > MAX_SIZE) {
            throw new ValidationException(
                SIZE_FIELD,
                "size must be between 1 and " + MAX_SIZE
            );
        }
        return size;
    }
}
