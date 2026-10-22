package com.unihub.workshop.iam.api;

import com.unihub.workshop.iam.api.dto.StaffUserDto;
import com.unihub.workshop.iam.api.dto.StudentProfileDto;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Public synchronous facade interface for IAM queries required by peer modules.
 */
public interface IamApi {
    /**
     * Resolves multiple staff user profiles in batch by their user IDs.
     *
     * @param userIds collection of user IDs to resolve
     * @return map of userId to StaffUserDto for all matching users found
     */
    Map<UUID, StaffUserDto> getStaffUsersByIds(Collection<UUID> userIds);

    /**
     * Resolves a single staff user profile by user ID.
     *
     * @param userId target user ID
     * @return optional containing StaffUserDto if user exists, empty otherwise
     */
    Optional<StaffUserDto> getStaffUserById(UUID userId);

    /**
     * Lists all active staff users belonging to the specified organization.
     *
     * @param organizationId target organization identifier
     * @return list of active StaffUserDto records
     */
    List<StaffUserDto> listActiveStaffByOrganization(UUID organizationId);

    /**
     * Resolves a student user profile by user ID.
     *
     * @param studentUserId target student user ID
     * @return optional containing StudentProfileDto if student exists and is active, empty otherwise
     */
    Optional<StudentProfileDto> getStudentProfile(UUID studentUserId);

    /**
     * Resolves multiple active student profiles in one batch by their user IDs.
     *
     * @param studentUserIds student user identifiers to resolve
     * @return active student profiles keyed by user ID; empty when no ids are supplied
     */
    Map<UUID, StudentProfileDto> getStudentProfilesByIds(
        Collection<UUID> studentUserIds
    );
}
