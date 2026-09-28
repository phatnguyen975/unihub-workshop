package com.unihub.workshop.shared.storage;

import com.unihub.workshop.shared.exception.InternalServerException;
import java.io.InputStream;
import java.net.URI;
import java.util.Collection;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Slf4j
@Component
public class MinioMediaStorageAdapter implements MediaStoragePort {

    private final S3Client s3Client;
    private final String mediaBucket;
    private final String publicUrlBase;

    public MinioMediaStorageAdapter(
        S3Client s3Client,
        @Value("${aws.s3.media-bucket:unihub-media}") String mediaBucket,
        @Value("${aws.s3.endpoint:http://localhost:9000}") String endpoint,
        @Value("${aws.s3.public-url-base:#{null}}") String publicUrlBase
    ) {
        this.s3Client = s3Client;
        this.mediaBucket = mediaBucket;
        if (publicUrlBase != null && !publicUrlBase.isBlank()) {
            this.publicUrlBase = stripTrailingSlash(publicUrlBase.trim());
        } else {
            this.publicUrlBase =
                stripTrailingSlash(endpoint.trim()) + "/" + mediaBucket;
        }
    }

    @Override
    public StoredMedia upload(
        InputStream inputStream,
        long size,
        String contentType,
        MediaAssetType assetType,
        String extension
    ) {
        String normalizedExt = extension.startsWith(".")
            ? extension
            : "." + extension;
        String fileKey =
            assetType.getFolderPrefix() +
            "/" +
            UUID.randomUUID() +
            normalizedExt;

        try {
            PutObjectRequest putRequest = PutObjectRequest.builder()
                .bucket(mediaBucket)
                .key(fileKey)
                .contentType(contentType)
                .contentLength(size)
                .build();

            s3Client.putObject(
                putRequest,
                RequestBody.fromInputStream(inputStream, size)
            );

            String publicUrl = publicUrlBase + "/" + fileKey;
            return new StoredMedia(
                fileKey,
                publicUrl,
                assetType,
                size,
                contentType
            );
        } catch (Exception e) {
            log.error(
                "Failed to upload media to MinIO: bucket={}, key={}",
                mediaBucket,
                fileKey,
                e
            );
            throw new InternalServerException(
                "Storage provider error during MinIO upload",
                e
            );
        }
    }

    @Override
    public void delete(String fileKey) {
        if (fileKey == null || fileKey.isBlank()) {
            return;
        }
        try {
            DeleteObjectRequest deleteRequest = DeleteObjectRequest.builder()
                .bucket(mediaBucket)
                .key(fileKey.trim())
                .build();
            s3Client.deleteObject(deleteRequest);
            log.debug(
                "Successfully deleted object from storage: bucket={}, key={}",
                mediaBucket,
                fileKey
            );
        } catch (Exception e) {
            log.warn(
                "Failed to delete object from storage: bucket={}, key={}, error={}",
                mediaBucket,
                fileKey,
                e.getMessage()
            );
        }
    }

    @Override
    public void deleteByUrl(String publicUrl) {
        String fileKey = extractFileKeyFromUrl(publicUrl);
        if (fileKey != null) {
            delete(fileKey);
        }
    }

    @Override
    public void deleteAllByUrls(Collection<String> publicUrls) {
        if (publicUrls == null || publicUrls.isEmpty()) {
            return;
        }
        for (String url : publicUrls) {
            deleteByUrl(url);
        }
    }

    public String extractFileKeyFromUrl(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        try {
            URI uri = URI.create(url.trim());
            String path = uri.getPath();
            if (path == null) {
                return null;
            }
            String bucketPrefix = "/" + mediaBucket + "/";
            int bucketIdx = path.indexOf(bucketPrefix);
            if (bucketIdx >= 0) {
                return path.substring(bucketIdx + bucketPrefix.length());
            }

            // Fallback for virtual-hosted style (e.g. https://unihub-media.s3.amazonaws.com/...)
            // or CDN domains (e.g. https://media.unihub.edu.vn/...)
            for (MediaAssetType assetType : MediaAssetType.values()) {
                String prefix = "/" + assetType.getFolderPrefix() + "/";
                int idx = path.indexOf(prefix);
                if (idx >= 0) {
                    return path.substring(idx + 1);
                }
            }

            return null;
        } catch (Exception e) {
            log.debug(
                "Could not parse file key from URL {}: {}",
                url,
                e.getMessage()
            );
            return null;
        }
    }

    private static String stripTrailingSlash(String str) {
        if (str == null) {
            return null;
        }
        if (str.endsWith("/")) {
            return str.substring(0, str.length() - 1);
        }
        return str;
    }
}
