package com.unihub.workshop.iam.application.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.unihub.workshop.iam.api.dto.StaffUserDto;
import com.unihub.workshop.iam.api.dto.StudentProfileDto;
import com.unihub.workshop.iam.application.ports.out.StudentDirectoryRepository;
import com.unihub.workshop.iam.application.ports.out.UserRepository;
import com.unihub.workshop.iam.domain.model.StudentDirectory;
import com.unihub.workshop.iam.domain.model.User;
import com.unihub.workshop.iam.domain.model.UserRole;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class IamFacadeTest {

    private UserRepository userRepository;
    private StudentDirectoryRepository studentDirectoryRepository;
    private IamFacade iamFacade;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        studentDirectoryRepository = mock(StudentDirectoryRepository.class);
        iamFacade = new IamFacade(userRepository, studentDirectoryRepository);
    }

    @Test
    @DisplayName("getStudentProfile: returns empty when studentUserId is null")
    void getStudentProfile_NullUserId_ReturnsEmpty() {
        Optional<StudentProfileDto> result = iamFacade.getStudentProfile(null);

        assertThat(result).isEmpty();
        verifyNoInteractions(userRepository);
    }

    @Test
    @DisplayName("getStudentProfile: returns empty when user is not found")
    void getStudentProfile_UserNotFound_ReturnsEmpty() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        Optional<StudentProfileDto> result = iamFacade.getStudentProfile(
            userId
        );

        assertThat(result).isEmpty();
        verify(userRepository).findById(userId);
    }

    @Test
    @DisplayName("getStudentProfile: returns empty when user is inactive")
    void getStudentProfile_UserInactive_ReturnsEmpty() {
        UUID userId = UUID.randomUUID();
        User inactiveStudent = User.builder()
            .id(userId)
            .role(UserRole.STUDENT)
            .isActive(false)
            .fullName("Jane Doe")
            .email("jane@university.edu")
            .studentRecordId(UUID.randomUUID())
            .build();
        when(userRepository.findById(userId)).thenReturn(
            Optional.of(inactiveStudent)
        );

        Optional<StudentProfileDto> result = iamFacade.getStudentProfile(
            userId
        );

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("getStudentProfile: returns empty when user is not a STUDENT")
    void getStudentProfile_UserNotStudent_ReturnsEmpty() {
        UUID userId = UUID.randomUUID();
        User organizer = User.builder()
            .id(userId)
            .role(UserRole.ORGANIZER)
            .isActive(true)
            .fullName("Admin Organizer")
            .email("org@university.edu")
            .build();
        when(userRepository.findById(userId)).thenReturn(
            Optional.of(organizer)
        );

        Optional<StudentProfileDto> result = iamFacade.getStudentProfile(
            userId
        );

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName(
        "getStudentProfile: returns StudentProfileDto when active student with studentRecordId"
    )
    void getStudentProfile_ValidLinkedStudent_ReturnsDto() {
        UUID userId = UUID.randomUUID();
        UUID recordId = UUID.randomUUID();
        User student = User.builder()
            .id(userId)
            .role(UserRole.STUDENT)
            .isActive(true)
            .fullName("John Doe")
            .email("john@university.edu")
            .studentRecordId(recordId)
            .build();
        when(userRepository.findById(userId)).thenReturn(Optional.of(student));
        when(
            studentDirectoryRepository.findAllByIds(Set.of(recordId))
        ).thenReturn(List.of(studentDirectory(recordId, "23123456")));

        Optional<StudentProfileDto> result = iamFacade.getStudentProfile(
            userId
        );

        assertThat(result).isPresent();
        StudentProfileDto dto = result.get();
        assertThat(dto.userId()).isEqualTo(userId);
        assertThat(dto.studentRecordId()).isEqualTo(recordId);
        assertThat(dto.fullName()).isEqualTo("John Doe");
        assertThat(dto.email()).isEqualTo("john@university.edu");
        assertThat(dto.studentNumber()).isEqualTo("23123456");
    }

    @Test
    @DisplayName(
        "getStudentProfile: keeps a null student number when the directory record is absent"
    )
    void getStudentProfile_WithoutDirectoryRecord_ReturnsNullStudentNumber() {
        UUID userId = UUID.randomUUID();
        UUID recordId = UUID.randomUUID();
        User student = User.builder()
            .id(userId)
            .role(UserRole.STUDENT)
            .isActive(true)
            .fullName("John Doe")
            .email("john@university.edu")
            .studentRecordId(recordId)
            .build();
        when(userRepository.findById(userId)).thenReturn(Optional.of(student));
        when(
            studentDirectoryRepository.findAllByIds(Set.of(recordId))
        ).thenReturn(List.of());

        Optional<StudentProfileDto> result = iamFacade.getStudentProfile(
            userId
        );

        assertThat(result).isPresent();
        assertThat(result.get().studentNumber()).isNull();
    }

    @Test
    @DisplayName(
        "getStudentProfile: returns StudentProfileDto with null studentRecordId when unlinked"
    )
    void getStudentProfile_UnlinkedStudent_ReturnsDtoWithNullRecordId() {
        UUID userId = UUID.randomUUID();
        User student = User.builder()
            .id(userId)
            .role(UserRole.STUDENT)
            .isActive(true)
            .fullName("Unlinked Student")
            .email("unlinked@university.edu")
            .studentRecordId(null)
            .build();
        when(userRepository.findById(userId)).thenReturn(Optional.of(student));

        Optional<StudentProfileDto> result = iamFacade.getStudentProfile(
            userId
        );

        assertThat(result).isPresent();
        StudentProfileDto dto = result.get();
        assertThat(dto.userId()).isEqualTo(userId);
        assertThat(dto.studentRecordId()).isNull();
        assertThat(dto.studentNumber()).isNull();
        assertThat(dto.fullName()).isEqualTo("Unlinked Student");
        assertThat(dto.email()).isEqualTo("unlinked@university.edu");
        verifyNoInteractions(studentDirectoryRepository);
    }

    @Test
    @DisplayName(
        "getStudentProfilesByIds: returns active and inactive students with their directory student numbers"
    )
    void getStudentProfilesByIds_ReturnsActiveAndInactiveStudentsWithStudentNumbers() {
        UUID firstUserId = UUID.randomUUID();
        UUID secondUserId = UUID.randomUUID();
        UUID inactiveUserId = UUID.randomUUID();
        UUID organizerUserId = UUID.randomUUID();
        UUID firstRecordId = UUID.randomUUID();
        UUID secondRecordId = UUID.randomUUID();
        UUID inactiveRecordId = UUID.randomUUID();

        User first = User.builder()
            .id(firstUserId)
            .role(UserRole.STUDENT)
            .isActive(true)
            .fullName("Alice Smith")
            .email("alice@university.edu")
            .studentRecordId(firstRecordId)
            .build();
        User second = User.builder()
            .id(secondUserId)
            .role(UserRole.STUDENT)
            .isActive(true)
            .fullName("Bob Tran")
            .email("bob@university.edu")
            .studentRecordId(secondRecordId)
            .build();
        User inactive = User.builder()
            .id(inactiveUserId)
            .role(UserRole.STUDENT)
            .isActive(false)
            .fullName("Inactive Student")
            .studentRecordId(inactiveRecordId)
            .build();
        User organizer = User.builder()
            .id(organizerUserId)
            .role(UserRole.ORGANIZER)
            .isActive(true)
            .fullName("Organizer")
            .build();

        Set<UUID> userIds = Set.of(
            firstUserId,
            secondUserId,
            inactiveUserId,
            organizerUserId
        );
        Set<UUID> recordIds = Set.of(
            firstRecordId,
            secondRecordId,
            inactiveRecordId
        );

        when(userRepository.findAllById(userIds)).thenReturn(
            List.of(first, second, inactive, organizer)
        );
        when(studentDirectoryRepository.findAllByIds(recordIds)).thenReturn(
            List.of(
                studentDirectory(firstRecordId, "23123456"),
                studentDirectory(secondRecordId, "23999999"),
                studentDirectory(inactiveRecordId, "23888888")
            )
        );

        Map<UUID, StudentProfileDto> result = iamFacade.getStudentProfilesByIds(
            userIds
        );

        assertThat(result).containsOnlyKeys(
            firstUserId,
            secondUserId,
            inactiveUserId
        );
        assertThat(result.get(firstUserId).fullName()).isEqualTo("Alice Smith");
        assertThat(result.get(firstUserId).studentNumber()).isEqualTo(
            "23123456"
        );
        assertThat(result.get(secondUserId).fullName()).isEqualTo("Bob Tran");
        assertThat(result.get(secondUserId).studentNumber()).isEqualTo(
            "23999999"
        );
        assertThat(result.get(inactiveUserId).fullName()).isEqualTo(
            "Inactive Student"
        );
        assertThat(result.get(inactiveUserId).studentNumber()).isEqualTo(
            "23888888"
        );
    }

    @Test
    @DisplayName(
        "getStudentProfilesByIds: returns an empty map without querying when no ids are supplied"
    )
    void getStudentProfilesByIds_WithoutIds_ReturnsEmptyMap() {
        assertThat(iamFacade.getStudentProfilesByIds(Set.of())).isEmpty();
        assertThat(iamFacade.getStudentProfilesByIds(null)).isEmpty();

        verifyNoInteractions(userRepository);
        verifyNoInteractions(studentDirectoryRepository);
    }

    private StudentDirectory studentDirectory(UUID id, String studentNumber) {
        return StudentDirectory.builder()
            .id(id)
            .organizationId(UUID.randomUUID())
            .studentNumber(studentNumber)
            .email("student@university.edu")
            .fullName("Student")
            .status("ACTIVE")
            .build();
    }

    @Test
    @DisplayName("getStaffUserById: delegates to userRepository")
    void getStaffUserById_ReturnsStaffUser() {
        UUID userId = UUID.randomUUID();
        User staff = User.builder()
            .id(userId)
            .organizationId(UUID.randomUUID())
            .role(UserRole.STAFF)
            .isActive(true)
            .fullName("Staff Member")
            .email("staff@university.edu")
            .build();
        when(userRepository.findById(userId)).thenReturn(Optional.of(staff));

        Optional<StaffUserDto> result = iamFacade.getStaffUserById(userId);

        assertThat(result).isPresent();
        assertThat(result.get().userId()).isEqualTo(userId);
        assertThat(result.get().fullName()).isEqualTo("Staff Member");
    }
}
