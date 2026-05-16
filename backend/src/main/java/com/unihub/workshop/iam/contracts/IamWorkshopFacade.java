package com.unihub.workshop.iam.contracts;

import java.util.UUID;

/**
 * Facade interface for workshop-related operations that require identity and
 * access verification.
 * Provides cross-module contract for the workshops module to verify staff and
 * organization relationships.
 */
public interface IamWorkshopFacade {

    /**
     * Verifies whether a staff member exists and belongs to a specific
     * organization.
     *
     * @param staffId        the UUID of the staff member
     * @param organizationId the UUID of the organization
     * @return true if the staff member exists and belongs to the organization;
     *         false otherwise
     */
    boolean verifyStaffExistsInOrganization(UUID staffId, UUID organizationId);

    /**
     * Verifies whether a user exists and has the STAFF role.
     *
     * @param userId the UUID of the user
     * @return true if the user exists and has the STAFF role; false otherwise
     */
    boolean verifyUserIsStaff(UUID userId);

    /**
     * Verifies whether an organization exists.
     *
     * @param organizationId the UUID of the organization
     * @return true if the organization exists; false otherwise
     */
    boolean verifyOrganizationExists(UUID organizationId);
}
