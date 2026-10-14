package com.unihub.workshop.iam.application.facade;

import com.unihub.workshop.iam.api.IamApi;
import com.unihub.workshop.iam.api.dto.StaffUserDto;
import com.unihub.workshop.iam.api.dto.StudentProfileDto;
import com.unihub.workshop.iam.application.ports.out.UserRepository;
import com.unihub.workshop.iam.domain.model.User;
import com.unihub.workshop.iam.domain.model.UserRole;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
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

    @Override
    @Transactional(readOnly = true)
    public List<StaffUserDto> listActiveStaffByOrganization(
        UUID organizationId
    ) {
        if (organizationId == null) {
            return List.of();
        }
        return userRepository
            .findActiveStaffByOrganizationId(organizationId)
            .stream()
            .map(this::toStaffUserDto)
            .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StudentProfileDto> getStudentProfile(UUID studentUserId) {
        if (studentUserId == null) {
            return Optional.empty();
        }
        return userRepository
            .findById(studentUserId)
            .filter(User::isActive)
            .filter(user -> user.role() == UserRole.STUDENT)
            .map(this::toStudentProfileDto);
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

    private StudentProfileDto toStudentProfileDto(User user) {
        return new StudentProfileDto(
            user.id(),
            user.studentRecordId(),
            user.fullName(),
            user.email()
        );
    }
}
