package com.unihub.workshop.iam.application.facade;

import com.unihub.workshop.iam.api.IamApi;
import com.unihub.workshop.iam.api.dto.StaffUserDto;
import com.unihub.workshop.iam.application.ports.out.UserRepository;
import com.unihub.workshop.iam.domain.model.User;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementation of the public {@link IamApi} facade.
 * Coordinates internal IAM use cases/ports to satisfy cross-module requests.
 */
@Component
@RequiredArgsConstructor
public class IamFacade implements IamApi {

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, StaffUserDto> getStaffUsersByIds(
        Collection<UUID> userIds
    ) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return userRepository
            .findAllById(userIds)
            .stream()
            .map(this::toStaffUserDto)
            .collect(
                Collectors.toMap(StaffUserDto::userId, Function.identity())
            );
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StaffUserDto> getStaffUserById(UUID userId) {
        if (userId == null) {
            return Optional.empty();
        }
        return userRepository.findById(userId).map(this::toStaffUserDto);
    }

    private StaffUserDto toStaffUserDto(User user) {
        return new StaffUserDto(
            user.id(),
            user.organizationId(),
            user.role() != null ? user.role().name() : null,
            user.isActive(),
            user.fullName(),
            user.email(),
            user.avatarUrl(),
            user.phoneNumber()
        );
    }
}
