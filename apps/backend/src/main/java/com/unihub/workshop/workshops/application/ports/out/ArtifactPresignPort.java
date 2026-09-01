package com.unihub.workshop.workshops.application.ports.out;

import java.net.URL;
import java.time.Duration;

public interface ArtifactPresignPort {
    /**
     * Generates a pre-signed GET URL for downloading an artifact from the storage.
     * 
     * @param objectKey The key of the object in storage (file_rejection_source_key)
     * @param ttl       The duration for which the pre-signed URL is valid
     * @return          The pre-signed URL
     * @throws RuntimeException if URL generation fails
     */
    URL generateDownloadUrl(String objectKey, Duration ttl);
}
