/**
 * Registrations Module.
 * <p>
 * Manages workshop registrations, tickets, waitlists, and capacity allocation.
 */
@org.springframework.modulith.ApplicationModule(
    displayName = "Registrations Module",
    allowedDependencies = { "shared", "workshops::api" }
)
package com.unihub.workshop.registrations;
