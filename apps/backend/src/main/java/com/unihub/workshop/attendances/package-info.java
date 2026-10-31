/**
 * Attendances Module.
 *
 * <p>Handles offline check-in scan reconciliation: validates submitted tickets against the
 * registrations module, persists first-time check-ins, and classifies every submitted entry.
 */
@org.springframework.modulith.ApplicationModule(
    displayName = "Attendances Module",
    allowedDependencies = { "shared", "workshops::api", "registrations::api" }
)
package com.unihub.workshop.attendances;
