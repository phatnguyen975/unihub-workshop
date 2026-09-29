package com.unihub.workshop.iam.api;

import com.unihub.workshop.iam.api.dto.StaffUserDto;
import java.util.Collection;
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
}
