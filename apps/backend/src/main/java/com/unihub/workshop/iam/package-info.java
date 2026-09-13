/**
 * Identity and Access Management (IAM) Module.
 * <p>
 * Manages user identity lifecycle, authentication (JWT RS256, active sessions),
 * password reset, student identity verification, organization member management,
 * and public key distribution.
 */
@org.springframework.modulith.ApplicationModule(
    displayName = "IAM Module",
    allowedDependencies = { "shared" }
)
package com.unihub.workshop.iam;
