package com.unihub.workshop.workers.batch_ingestion.outbound.storage;

import com.unihub.workshop.workers.batch_ingestion.core.models.SourceFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class S3IngestionObjectStoreTest {

    private S3Client s3Client;
    private S3IngestionObjectStore store;
    private static final String BUCKET_NAME = "unihub-bucket";

    @BeforeEach
    void setUp() {
        s3Client = mock(S3Client.class);
        store = new S3IngestionObjectStore(s3Client, BUCKET_NAME);
    }

    @Test
    void shouldListInboxFiles() {
        var obj1 = S3Object.builder().key("inbox/it/f1.csv").size(1024L)
                .lastModified(Instant.parse("2026-08-31T01:00:00Z")).build();
        var obj2 = S3Object.builder().key("inbox/chem/f2.csv").size(2048L)
                .lastModified(Instant.parse("2026-08-31T02:00:00Z")).build();

        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(
                ListObjectsV2Response.builder().contents(obj1, obj2).build());

        List<SourceFile> files = store.listInboxFiles();

        assertThat(files).hasSize(2);
        assertThat(files.get(0).key()).isEqualTo("inbox/it/f1.csv");
        assertThat(files.get(0).organizationSlug()).isEqualTo("it");
        assertThat(files.get(0).sizeBytes()).isEqualTo(1024L);
        assertThat(files.get(0).createdAt()).isEqualTo(Instant.parse("2026-08-31T01:00:00Z"));

        assertThat(files.get(1).key()).isEqualTo("inbox/chem/f2.csv");
        assertThat(files.get(1).organizationSlug()).isEqualTo("chem");
    }

    @Test
    void shouldMoveToRejected() {
        String newKey = store.moveToRejected("inbox/it/f1.csv", "it");

        assertThat(newKey).isEqualTo("rejected/it/source-files/f1.csv");

        var copyCaptor = ArgumentCaptor.forClass(CopyObjectRequest.class);
        verify(s3Client).copyObject(copyCaptor.capture());
        assertThat(copyCaptor.getValue().sourceBucket()).isEqualTo(BUCKET_NAME);
        assertThat(copyCaptor.getValue().sourceKey()).isEqualTo("inbox/it/f1.csv");
        assertThat(copyCaptor.getValue().destinationBucket()).isEqualTo(BUCKET_NAME);
        assertThat(copyCaptor.getValue().destinationKey()).isEqualTo(newKey);

        var deleteCaptor = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client).deleteObject(deleteCaptor.capture());
        assertThat(deleteCaptor.getValue().bucket()).isEqualTo(BUCKET_NAME);
        assertThat(deleteCaptor.getValue().key()).isEqualTo("inbox/it/f1.csv");
    }

    @Test
    void shouldReturnTrueWhenHeaderIsValid() {
        String validHeader = "student_number,email,full_name,faculty\n231234,a@b,c,d";

        when(s3Client.getObject(any(GetObjectRequest.class),
                org.mockito.ArgumentMatchers
                        .<ResponseTransformer<GetObjectResponse, ResponseBytes<GetObjectResponse>>>any()))
                .thenReturn(ResponseBytes.fromByteArray(
                        GetObjectResponse.builder().build(),
                        validHeader.getBytes(StandardCharsets.UTF_8)));

        assertThat(store.hasValidHeader("inbox/it/f1.csv")).isTrue();
    }

    @Test
    void shouldReturnFalseWhenHeaderIsInvalid() {
        String invalidHeader = "student_number,email,organization_id,status\n231234,a@b,c,d";

        when(s3Client.getObject(any(GetObjectRequest.class),
                org.mockito.ArgumentMatchers
                        .<ResponseTransformer<GetObjectResponse, ResponseBytes<GetObjectResponse>>>any()))
                .thenReturn(ResponseBytes.fromByteArray(
                        GetObjectResponse.builder().build(),
                        invalidHeader.getBytes(StandardCharsets.UTF_8)));

        assertThat(store.hasValidHeader("inbox/it/f1.csv")).isFalse();
    }
}
