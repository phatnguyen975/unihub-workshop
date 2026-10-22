package com.unihub.workshop.iam.application.facade;

import com.unihub.workshop.iam.api.IamApi;
import com.unihub.workshop.iam.api.dto.StaffUserDto;
import com.unihub.workshop.iam.api.dto.StudentProfileDto;
import com.unihub.workshop.iam.application.ports.out.StudentDirectoryRepository;
import com.unihub.workshop.iam.application.ports.out.UserRepository;
import com.unihub.workshop.iam.domain.model.StudentDirectory;
import com.unihub.workshop.iam.domain.model.User;
import com.unihub.workshop.iam.domain.model.UserRole;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
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
    private final StudentDirectoryRepository studentDirectoryRepository;

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
            .map(user ->
                toStudentProfileDto(user, resolveStudentNumbers(List.of(user)))
            );
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, StudentProfileDto> getStudentProfilesByIds(
        Collection<UUID> studentUserIds
    ) {
        if (studentUserIds == null || studentUserIds.isEmpty()) {
            return Map.of();
        }
        List<User> students = userRepository
            .findAllById(studentUserIds)
            .stream()
            .filter(User::isActive)
            .filter(user -> user.role() == UserRole.STUDENT)
            .toList();
        if (students.isEmpty()) {
            return Map.of();
        }
        Map<UUID, String> studentNumbers = resolveStudentNumbers(students);
        return students
            .stream()
            .collect(
                Collectors.toMap(User::id, user ->
                    toStudentProfileDto(user, studentNumbers)
                )
            );
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

    private StudentProfileDto toStudentProfileDto(
        User user,
        Map<UUID, String> studentNumbers
    ) {
        UUID studentRecordId = user.studentRecordId();
        return new StudentProfileDto(
            user.id(),
            studentRecordId,
            user.fullName(),
            user.email(),
            studentRecordId != null ? studentNumbers.get(studentRecordId) : null
        );
    }

    private Map<UUID, String> resolveStudentNumbers(Collection<User> users) {
        Set<UUID> recordIds = users
            .stream()
            .map(User::studentRecordId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        if (recordIds.isEmpty()) {
            return Map.of();
        }
        return studentDirectoryRepository
            .findAllByIds(recordIds)
            .stream()
            .filter(directory -> directory.studentNumber() != null)
            .collect(
                Collectors.toMap(
                    StudentDirectory::id,
                    StudentDirectory::studentNumber
                )
            );
    }
}
