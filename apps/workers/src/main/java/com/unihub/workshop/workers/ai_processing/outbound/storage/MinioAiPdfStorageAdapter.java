package com.unihub.workshop.workers.ai_processing.outbound.storage;

import com.unihub.workshop.workers.ai_processing.core.ports.out.BrochureStoragePort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

@Slf4j
@Component
public class MinioAiPdfStorageAdapter implements BrochureStoragePort {

    private final S3Client s3Client;
    private final String bucketName;

    public MinioAiPdfStorageAdapter(
        S3Client s3Client,
        @Value("${app.ai.bucket:unihub-brochures}") String bucketName
    ) {
        this.s3Client = s3Client;
        this.bucketName = bucketName;
    }

    @Override
    public byte[] fetchBrochurePdf(String storageKey) {
        log.debug(
            "Fetching brochure PDF from bucket '{}' with key '{}'",
            bucketName,
            storageKey
        );
        GetObjectRequest request = GetObjectRequest.builder()
            .bucket(bucketName)
            .key(storageKey)
            .build();

        ResponseBytes<GetObjectResponse> responseBytes = s3Client.getObject(
            request,
            ResponseTransformer.toBytes()
        );
        return responseBytes.asByteArray();
    }
}
