package com.unihub.workshop.iam.application.ports.in;

import com.unihub.workshop.iam.domain.model.UserRole;
import com.unihub.workshop.shared.exception.BadRequestException;
import java.util.UUID;

public record ListMembersQuery(
    UUID organizationId,
    UUID callerId,
    UserRole role,
    Boolean isActive,
    String q,
    String cursor,
    Integer size
) {
    public ListMembersQuery {
        if (organizationId == null) {
            throw new BadRequestException("organizationId must not be null");
        }
        if (callerId == null) {
            throw new BadRequestException("callerId must not be null");
        }
        if (size == null) {
            size = 20;
        } else if (size < 1 || size > 100) {
            throw new BadRequestException("size must be between 1 and 100");
        }
        if (q != null) {
            q = q.trim();
            if (q.isEmpty()) {
                q = null;
            } else if (q.length() > 200) {
                throw new BadRequestException(
                    "q parameter cannot exceed 200 characters"
                );
            }
        }
    }
}
