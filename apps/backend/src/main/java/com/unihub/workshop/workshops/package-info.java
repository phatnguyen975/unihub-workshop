/**
 * Workshops Module.
 * <p>
 * Manages workshop lifecycle, scheduling, catalog, and ingestion job reporting.
 */
@org.springframework.modulith.ApplicationModule(
    displayName = "Workshops Module",
    allowedDependencies = { "shared" }
)
package com.unihub.workshop.workshops;
