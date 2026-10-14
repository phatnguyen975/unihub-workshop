package com.unihub.workshop.iam.application.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.unihub.workshop.iam.api.dto.StaffUserDto;
import com.unihub.workshop.iam.api.dto.StudentProfileDto;
import com.unihub.workshop.iam.application.ports.out.UserRepository;
import com.unihub.workshop.iam.domain.model.User;
import com.unihub.workshop.iam.domain.model.UserRole;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class IamFacadeTest {

    private UserRepository userRepository;
    private IamFacade iamFacade;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        iamFacade = new IamFacade(userRepository);
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

        Optional<StudentProfileDto> result = iamFacade.getStudentProfile(
            userId
        );

        assertThat(result).isPresent();
        StudentProfileDto dto = result.get();
        assertThat(dto.userId()).isEqualTo(userId);
        assertThat(dto.studentRecordId()).isEqualTo(recordId);
        assertThat(dto.fullName()).isEqualTo("John Doe");
        assertThat(dto.email()).isEqualTo("john@university.edu");
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
        assertThat(dto.fullName()).isEqualTo("Unlinked Student");
        assertThat(dto.email()).isEqualTo("unlinked@university.edu");
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
