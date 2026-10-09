/**
 * In-App Notification Management Module.
 * <p>
 * Manages user in-app notification inbox, unread counts,
 * and read-state transitions with real-time SSE synchronization.
 */
@org.springframework.modulith.ApplicationModule(
    displayName = "Notifications Module",
    allowedDependencies = { "shared" }
)
package com.unihub.workshop.notifications;
