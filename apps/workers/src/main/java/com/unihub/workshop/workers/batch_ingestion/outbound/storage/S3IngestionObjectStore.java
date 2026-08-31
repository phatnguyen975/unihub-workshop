package com.unihub.workshop.workers.batch_ingestion.outbound.storage;

import com.unihub.workshop.workers.batch_ingestion.core.models.SourceFile;
import com.unihub.workshop.workers.batch_ingestion.core.ports.out.IngestionObjectStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
public class S3IngestionObjectStore implements IngestionObjectStore {

    private final S3Client s3Client;
    private final String bucketName;

    private static final Pattern INBOX_PATTERN = Pattern.compile("^inbox/([^/]+)/.*\\.csv$");
    private static final Set<String> REQUIRED_HEADERS = Set.of("student_number", "email", "full_name", "faculty");
    private static final Set<String> FORBIDDEN_HEADERS = Set.of("organization_id", "status");

    public S3IngestionObjectStore(
            S3Client s3Client,
            @Value("${app.storage.buckets.csv}") String bucketName) {
        this.s3Client = s3Client;
        this.bucketName = bucketName;
    }

    @Override
    public List<SourceFile> listInboxFiles() {
        ListObjectsV2Request req = ListObjectsV2Request.builder()
                .bucket(bucketName)
                .prefix("inbox/")
                .build();

        List<SourceFile> results = new ArrayList<>();
        ListObjectsV2Response res;
        do {
            res = s3Client.listObjectsV2(req);
            for (S3Object obj : res.contents()) {
                Matcher matcher = INBOX_PATTERN.matcher(obj.key());
                if (matcher.matches()) {
                    String orgSlug = matcher.group(1);
                    results.add(new SourceFile(obj.key(), orgSlug, obj.size(), obj.lastModified()));
                }
            }
            req = req.toBuilder().continuationToken(res.nextContinuationToken()).build();
        } while (Boolean.TRUE.equals(res.isTruncated()));

        return results;
    }

    @Override
    public boolean hasValidHeader(String fileKey) {
        GetObjectRequest req = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(fileKey)
                .range("bytes=0-1023") // Only fetch first 1024 bytes for header
                .build();

        try {
            ResponseBytes<GetObjectResponse> res = s3Client.getObject(req, ResponseTransformer.toBytes());
            String head = new String(res.asByteArray(), StandardCharsets.UTF_8);

            String firstLine = head.split("\\r?\\n")[0];
            Set<String> headers = Arrays.stream(firstLine.split(","))
                    .map(String::trim)
                    .collect(Collectors.toSet());

            return headers.containsAll(REQUIRED_HEADERS) &&
                    headers.stream().noneMatch(FORBIDDEN_HEADERS::contains);
        } catch (Exception e) {
            // Either file is too small or other issue, but effectively invalid header
            return false;
        }
    }

    @Override
    public String moveToRejected(String fileKey, String organizationSlug) {
        String filename = fileKey.substring(fileKey.lastIndexOf('/') + 1);
        String destinationKey = "rejected/" + organizationSlug + "/source-files/" + filename;

        s3Client.copyObject(CopyObjectRequest.builder()
                .sourceBucket(bucketName)
                .sourceKey(fileKey)
                .destinationBucket(bucketName)
                .destinationKey(destinationKey)
                .build());

        s3Client.deleteObject(DeleteObjectRequest.builder()
                .bucket(bucketName)
                .key(fileKey)
                .build());

        return destinationKey;
    }

    @Override
    public String moveToArchive(String fileKey, String organizationSlug) {
        String filename = fileKey.substring(fileKey.lastIndexOf('/') + 1);
        String destinationKey = "archive/" + organizationSlug + "/" + filename;

        s3Client.copyObject(CopyObjectRequest.builder()
                .sourceBucket(bucketName)
                .sourceKey(fileKey)
                .destinationBucket(bucketName)
                .destinationKey(destinationKey)
                .build());

        s3Client.deleteObject(DeleteObjectRequest.builder()
                .bucket(bucketName)
                .key(fileKey)
                .build());

        return destinationKey;
    }
}
