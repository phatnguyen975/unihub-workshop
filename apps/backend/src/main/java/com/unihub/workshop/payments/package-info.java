/**
 * Payments Module.
 * <p>
 * Handles external payment gateway integration, transaction records, and payment webhooks.
 */
@org.springframework.modulith.ApplicationModule(
    displayName = "Payments Module",
    allowedDependencies = {
        "shared",
        "registrations::api",
        "iam::api",
        "workshops::api",
    }
)
package com.unihub.workshop.payments;
