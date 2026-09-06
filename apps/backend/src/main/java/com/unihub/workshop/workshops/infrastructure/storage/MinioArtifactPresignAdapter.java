package com.unihub.workshop.workshops.infrastructure.storage;

import com.unihub.workshop.workshops.application.ports.out.ArtifactPresignPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.net.URI;
import java.net.URL;
import java.time.Duration;

@Component
public class MinioArtifactPresignAdapter implements ArtifactPresignPort {

    private S3Presigner s3Presigner;
    private String bucketName;

    public MinioArtifactPresignAdapter(
            @Value("${aws.s3.endpoint:#{null}}") String endpoint,
            @Value("${aws.region:ap-southeast-1}") String region,
            @Value("${aws.access-key-id:}") String accessKey,
            @Value("${aws.secret-access-key:}") String secretKey,
            @Value("${aws.s3.bucket:unihub-csv-drops}") String bucketName) {

        S3Presigner.Builder builder = S3Presigner.builder()
                .region(Region.of(region));

        if (endpoint != null && !endpoint.isBlank()) {
            builder.endpointOverride(URI.create(endpoint));
        }

        if (accessKey != null && !accessKey.isBlank() && secretKey != null && !secretKey.isBlank()) {
            builder.credentialsProvider(StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(accessKey, secretKey)));
        }

        this.s3Presigner = builder.build();
        this.bucketName = bucketName;
    }

    void setS3Presigner(S3Presigner s3Presigner) {
        this.s3Presigner = s3Presigner;
    }

    @Override
    public URL generateDownloadUrl(String objectKey, Duration ttl) {
        try {
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(objectKey)
                    .build();

            GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                    .signatureDuration(ttl)
                    .getObjectRequest(getObjectRequest)
                    .build();

            PresignedGetObjectRequest presignedRequest = s3Presigner.presignGetObject(presignRequest);

            return presignedRequest.url();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate pre-signed URL for artifact", e);
        }
    }
}
