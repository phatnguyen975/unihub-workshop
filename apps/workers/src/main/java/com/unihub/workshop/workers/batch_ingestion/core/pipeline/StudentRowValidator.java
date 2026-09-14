package com.unihub.workshop.workers.batch_ingestion.core.pipeline;

import com.unihub.workshop.workers.batch_ingestion.core.models.RejectedStudentRow;
import com.unihub.workshop.workers.batch_ingestion.core.models.StudentDirectoryRow;
import com.unihub.workshop.workers.batch_ingestion.core.ports.out.RejectArtifactStore;
import java.util.regex.Pattern;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@StepScope
public class StudentRowValidator
    implements ItemProcessor<StudentDirectoryRow, StudentDirectoryRow>
{

    private final String expectedOrganizationName;
    private final RejectArtifactStore rejectArtifactStore;
    private final Long jobExecutionId;
    private static final Pattern STUDENT_NUMBER_PATTERN = Pattern.compile(
        "^23\\d{6}$"
    );

    public StudentRowValidator(
        @Value(
            "#{jobParameters['organizationName']}"
        ) String expectedOrganizationName,
        RejectArtifactStore rejectArtifactStore,
        @Value("#{stepExecution.jobExecutionId}") Long jobExecutionId
    ) {
        this.expectedOrganizationName = expectedOrganizationName;
        this.rejectArtifactStore = rejectArtifactStore;
        this.jobExecutionId = jobExecutionId;
    }

    @Override
    public StudentDirectoryRow process(StudentDirectoryRow item) {
        if (
            isBlank(item.studentNumber()) ||
            isBlank(item.email()) ||
            isBlank(item.fullName()) ||
            isBlank(item.faculty())
        ) {
            rejectArtifactStore.append(
                jobExecutionId,
                createReject(item, "Blank required fields")
            );
            return null;
        }

        if (!STUDENT_NUMBER_PATTERN.matcher(item.studentNumber()).matches()) {
            rejectArtifactStore.append(
                jobExecutionId,
                createReject(item, "Invalid student number format")
            );
            return null;
        }

        String expectedEmail = item.studentNumber() + "@student.hcmus.edu.vn";
        if (!expectedEmail.equals(item.email())) {
            rejectArtifactStore.append(
                jobExecutionId,
                createReject(item, "Email does not match student number")
            );
            return null;
        }

        if (
            item.fullName().length() > 200 ||
            item.faculty().length() > 200 ||
            item.studentNumber().length() > 50 ||
            item.email().length() > 255
        ) {
            rejectArtifactStore.append(
                jobExecutionId,
                createReject(item, "Overlength fields")
            );
            return null;
        }

        if (
            expectedOrganizationName == null ||
            !expectedOrganizationName.equals(item.faculty())
        ) {
            rejectArtifactStore.append(
                jobExecutionId,
                createReject(item, "Faculty does not match expected faculty")
            );
            return null;
        }

        return item;
    }

    private RejectedStudentRow createReject(
        StudentDirectoryRow item,
        String reason
    ) {
        return new RejectedStudentRow(
            item.studentNumber(),
            item.email(),
            item.fullName(),
            item.faculty(),
            reason
        );
    }

    private boolean isBlank(String str) {
        return str == null || str.trim().isEmpty();
    }
}
