/**
 * Shared Kernel Module.
 * <p>
 * Provides cross-cutting platform infrastructure and utilities, including:
 * <ul>
 *   <li>{@code config}: Common Redis, RabbitMQ, S3/MinIO infrastructure beans</li>
 *   <li>{@code database}: Dynamic primary/replica read-routing datasource</li>
 *   <li>{@code events}: Transactional outbox integration events for asynchronous worker notification</li>
 *   <li>{@code exception}: RFC 7807 global exception handling hierarchy</li>
 *   <li>{@code ratelimit}: Token-bucket distributed rate limiting filter via Bucket4j and Redis</li>
 *   <li>{@code realtime}: Server-Sent Events (SSE) streaming hub and Redis pub/sub distribution</li>
 *   <li>{@code resilience}: Resilience4j circuit breakers and chaos test adapters</li>
 *   <li>{@code security}: Asymmetric JWT RS256 token validation, JTI revocation, and security utilities</li>
 *   <li>{@code seed}: Database initialization and root account provisioning</li>
 *   <li>{@code web}: Common API response envelope and web formatting</li>
 * </ul>
 * This module is configured as {@link org.springframework.modulith.ApplicationModule.Type#OPEN}
 * and must never depend on any business domain module.
 * </p>
 */
@org.springframework.modulith.ApplicationModule(
    displayName = "Shared Kernel",
    type = org.springframework.modulith.ApplicationModule.Type.OPEN
)
package com.unihub.workshop.shared;
