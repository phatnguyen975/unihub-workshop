package com.unihub.workshop.workers.batch_ingestion.core.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.unihub.workshop.workers.batch_ingestion.core.models.RejectedStudentRow;
import com.unihub.workshop.workers.batch_ingestion.core.models.StudentDirectoryRow;
import com.unihub.workshop.workers.batch_ingestion.core.ports.out.RejectArtifactStore;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class StudentRowValidatorTest {

    private RejectArtifactStore rejectArtifactStore;
    private StudentRowValidator validator;
    private final long jobExecutionId = 123L;

    @BeforeEach
    void setUp() {
        rejectArtifactStore = mock(RejectArtifactStore.class);
        validator = new StudentRowValidator(
            "Information Technology",
            rejectArtifactStore,
            jobExecutionId
        );
    }

    @Test
    void shouldReturnItemWhenValid() {
        StudentDirectoryRow row = new StudentDirectoryRow(
            UUID.randomUUID(),
            "23120001",
            "23120001@student.hcmus.edu.vn",
            "John Doe",
            "Information Technology"
        );
        StudentDirectoryRow result = validator.process(row);

        assertThat(result).isNotNull();
        verify(rejectArtifactStore, never()).append(anyLong(), any());
    }

    @Test
    void shouldRejectWhenBlankFields() {
        StudentDirectoryRow row = new StudentDirectoryRow(
            UUID.randomUUID(),
            "",
            "23120001@student.hcmus.edu.vn",
            "John Doe",
            "Information Technology"
        );
        StudentDirectoryRow result = validator.process(row);

        assertThat(result).isNull();

        ArgumentCaptor<RejectedStudentRow> captor = ArgumentCaptor.forClass(
            RejectedStudentRow.class
        );
        verify(rejectArtifactStore).append(
            eq(jobExecutionId),
            captor.capture()
        );
        assertThat(captor.getValue().rejectReason()).isEqualTo(
            "Blank required fields"
        );
    }

    @Test
    void shouldRejectWhenInvalidStudentNumberFormat() {
        StudentDirectoryRow row = new StudentDirectoryRow(
            UUID.randomUUID(),
            "22120001",
            "22120001@student.hcmus.edu.vn",
            "John Doe",
            "Information Technology"
        );
        StudentDirectoryRow result = validator.process(row);

        assertThat(result).isNull();
        ArgumentCaptor<RejectedStudentRow> captor = ArgumentCaptor.forClass(
            RejectedStudentRow.class
        );
        verify(rejectArtifactStore).append(
            eq(jobExecutionId),
            captor.capture()
        );
        assertThat(captor.getValue().rejectReason()).isEqualTo(
            "Invalid student number format"
        );
    }

    @Test
    void shouldRejectWhenEmailDoesNotMatch() {
        StudentDirectoryRow row = new StudentDirectoryRow(
            UUID.randomUUID(),
            "23120001",
            "wrong@student.hcmus.edu.vn",
            "John Doe",
            "Information Technology"
        );
        StudentDirectoryRow result = validator.process(row);

        assertThat(result).isNull();
        ArgumentCaptor<RejectedStudentRow> captor = ArgumentCaptor.forClass(
            RejectedStudentRow.class
        );
        verify(rejectArtifactStore).append(
            eq(jobExecutionId),
            captor.capture()
        );
        assertThat(captor.getValue().rejectReason()).isEqualTo(
            "Email does not match student number"
        );
    }

    @Test
    void shouldRejectWhenFacultyNotExpected() {
        StudentDirectoryRow row = new StudentDirectoryRow(
            UUID.randomUUID(),
            "23120001",
            "23120001@student.hcmus.edu.vn",
            "John Doe",
            "BIO"
        );
        StudentDirectoryRow result = validator.process(row);

        assertThat(result).isNull();
        ArgumentCaptor<RejectedStudentRow> captor = ArgumentCaptor.forClass(
            RejectedStudentRow.class
        );
        verify(rejectArtifactStore).append(
            eq(jobExecutionId),
            captor.capture()
        );
        assertThat(captor.getValue().rejectReason()).isEqualTo(
            "Faculty does not match expected faculty"
        );
    }
}
