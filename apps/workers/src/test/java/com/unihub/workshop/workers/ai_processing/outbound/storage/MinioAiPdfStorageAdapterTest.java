package com.unihub.workshop.workers.ai_processing.outbound.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

class MinioAiPdfStorageAdapterTest {

    private S3Client s3Client;
    private MinioAiPdfStorageAdapter adapter;

    @BeforeEach
    void setUp() {
        s3Client = mock(S3Client.class);
        adapter = new MinioAiPdfStorageAdapter(s3Client, "unihub-brochures");
    }

    @Test
    @DisplayName("Should fetch brochure PDF bytes from S3 client")
    void shouldFetchPdfBytesSuccessfully() {
        String storageKey = "workshops/w1/brochures/b1.pdf";
        byte[] expectedContent = "%PDF-1.4 sample content".getBytes(
            StandardCharsets.UTF_8
        );

        GetObjectResponse getObjectResponse =
            GetObjectResponse.builder().build();
        ResponseBytes<GetObjectResponse> responseBytes =
            ResponseBytes.fromByteArray(getObjectResponse, expectedContent);

        when(
            s3Client.getObject(
                any(GetObjectRequest.class),
                ArgumentMatchers.<
                    ResponseTransformer<
                        GetObjectResponse,
                        ResponseBytes<GetObjectResponse>
                    >
                >any()
            )
        ).thenReturn(responseBytes);

        byte[] actual = adapter.fetchBrochurePdf(storageKey);

        assertThat(actual).isEqualTo(expectedContent);
    }
}
