package com.unihub.workshop.workers.batch_ingestion.outbound.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.unihub.workshop.workers.batch_ingestion.core.models.RejectedStudentRow;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

class S3RejectArtifactStoreTest {

    private S3Client s3Client;
    private S3RejectArtifactStore store;

    @BeforeEach
    void setUp() {
        s3Client = mock(S3Client.class);
        store = new S3RejectArtifactStore(
            s3Client,
            "unihub-csv-drops",
            "build/tmp/test-resources"
        );
    }

    @Test
    void shouldAppendAndUpload() {
        long jobId = 999L;
        RejectedStudentRow row = new RejectedStudentRow(
            "23120002",
            "email",
            "name",
            "FIT",
            "reason"
        );

        store.append(jobId, row);
        store.uploadAndCleanUp(jobId, "it", "inbox/it/students.csv");

        ArgumentCaptor<PutObjectRequest> putCaptor = ArgumentCaptor.forClass(
            PutObjectRequest.class
        );
        ArgumentCaptor<Path> pathCaptor = ArgumentCaptor.forClass(Path.class);

        verify(s3Client).putObject(putCaptor.capture(), pathCaptor.capture());

        PutObjectRequest request = putCaptor.getValue();
        assertThat(request.bucket()).isEqualTo("unihub-csv-drops");
        assertThat(request.key()).isEqualTo(
            "rejected/it/row-reports/students-quarantine.csv"
        );

        // Path should have been deleted, but we can check the path captured
        assertThat(pathCaptor.getValue().toString()).contains(
            "rejected_999.csv"
        );
    }
}
