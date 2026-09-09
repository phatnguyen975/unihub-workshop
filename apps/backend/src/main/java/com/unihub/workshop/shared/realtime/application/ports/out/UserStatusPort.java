package com.unihub.workshop.shared.realtime.application.ports.out;

import java.util.UUID;

/**
 * Outbound port to verify user account status (e.g. is_active) before
 * establishing streaming connection.
 */
public interface UserStatusPort {

    /**
     * Checks if the user with the given ID is active and not banned.
     *
     * @param userId user identifier
     * @return true if active, false otherwise
     */
    boolean isUserActive(UUID userId);
}
