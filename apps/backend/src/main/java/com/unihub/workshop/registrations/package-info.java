/**
 * Registrations Module.
 * <p>
 * Manages workshop registrations, tickets, waitlists, and capacity allocation.
 */
@org.springframework.modulith.ApplicationModule(
    displayName = "Registrations Module",
    allowedDependencies = { "shared", "workshops::api", "iam::api" }
)
package com.unihub.workshop.registrations;
