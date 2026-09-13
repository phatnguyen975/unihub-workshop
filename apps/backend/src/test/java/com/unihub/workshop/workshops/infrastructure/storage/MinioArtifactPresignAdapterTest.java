package com.unihub.workshop.workshops.infrastructure.storage;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

class MinioArtifactPresignAdapterTest {

    private S3Presigner s3Presigner;
    private MinioArtifactPresignAdapter adapter;
    private static final String BUCKET = "test-bucket";

    @BeforeEach
    void setUp() {
        s3Presigner = mock(S3Presigner.class);
        adapter = new MinioArtifactPresignAdapter(
            null,
            "ap-southeast-1",
            "",
            "",
            BUCKET
        );
        adapter.setS3Presigner(s3Presigner);
    }

    @Test
    void generateDownloadUrl_Success() throws MalformedURLException {
        PresignedGetObjectRequest mockPresigned = mock(
            PresignedGetObjectRequest.class
        );
        URL expectedUrl = URI.create(
            "https://minio/test-bucket/obj.csv?X-Amz-Expires=900"
        ).toURL();
        when(mockPresigned.url()).thenReturn(expectedUrl);
        when(
            s3Presigner.presignGetObject(any(GetObjectPresignRequest.class))
        ).thenReturn(mockPresigned);

        URL result = adapter.generateDownloadUrl(
            "obj.csv",
            Duration.ofMinutes(15)
        );

        assertEquals(expectedUrl, result);
    }

    @Test
    void generateDownloadUrl_SdkThrowsException_WrapsInRuntimeException() {
        when(
            s3Presigner.presignGetObject(any(GetObjectPresignRequest.class))
        ).thenThrow(new RuntimeException("S3 Error"));

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
            adapter.generateDownloadUrl("obj.csv", Duration.ofMinutes(15))
        );

        assertTrue(
            ex.getMessage().contains("Failed to generate pre-signed URL")
        );
        assertEquals("S3 Error", ex.getCause().getMessage());
    }
}
