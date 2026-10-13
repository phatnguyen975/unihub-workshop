package com.unihub.workshop.workshops.infrastructure.storage;

import com.unihub.workshop.shared.exception.InternalServerException;
import com.unihub.workshop.workshops.application.ports.out.BrochureStoragePort;
import java.io.InputStream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Slf4j
@Component
public class MinioBrochureStorageAdapter implements BrochureStoragePort {

    private final S3Client s3Client;
    private final String brochuresBucket;

    public MinioBrochureStorageAdapter(
        S3Client s3Client,
        @Value(
            "${aws.s3.brochures-bucket:unihub-brochures}"
        ) String brochuresBucket
    ) {
        this.s3Client = s3Client;
        this.brochuresBucket = brochuresBucket;
    }

    @Override
    public void uploadBrochure(
        String storageKey,
        InputStream inputStream,
        long byteSize,
        String contentType
    ) {
        try {
            PutObjectRequest putRequest = PutObjectRequest.builder()
                .bucket(brochuresBucket)
                .key(storageKey)
                .contentType(contentType)
                .contentLength(byteSize)
                .build();

            s3Client.putObject(
                putRequest,
                RequestBody.fromInputStream(inputStream, byteSize)
            );
            log.info(
                "Brochure uploaded successfully to MinIO: bucket={}, key={}",
                brochuresBucket,
                storageKey
            );
        } catch (Exception e) {
            log.error(
                "Failed to upload brochure to MinIO: bucket={}, key={}",
                brochuresBucket,
                storageKey,
                e
            );
            throw new InternalServerException(
                "Storage provider error during MinIO upload",
                e
            );
        }
    }
}
