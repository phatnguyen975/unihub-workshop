package com.unihub.workshop.shared.storage;

import java.io.InputStream;
import java.util.Collection;

public interface MediaStoragePort {
    /**
     * Streams media content to object storage under the partitioned prefix for the given media type.
     *
     * @param inputStream the media binary stream
     * @param size size in bytes
     * @param contentType MIME type (e.g. "image/jpeg")
     * @param mediaType media category determining storage folder partition
     * @param extension file extension including leading dot (e.g. ".jpg")
     * @return StoredMedia containing fileKey, publicUrl, size, contentType, and mediaType
     */
    StoredMedia upload(
        InputStream inputStream,
        long size,
        String contentType,
        MediaType mediaType,
        String extension
    );

    /**
     * Deletes an object by its storage key.
     *
     * @param fileKey relative key in storage bucket
     */
    void delete(String fileKey);

    /**
     * Deletes an object given its public URL. Safe no-op if URL is null/blank or outside the media bucket.
     *
     * @param publicUrl public URL of the object
     */
    void deleteByUrl(String publicUrl);

    /**
     * Deletes multiple objects given their public URLs.
     *
     * @param publicUrls collection of public URLs to delete
     */
    void deleteAllByUrls(Collection<String> publicUrls);
}
