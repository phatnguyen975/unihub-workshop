package com.unihub.workshop.shared.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.unihub.workshop.shared.exception.InternalServerException;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

@ExtendWith(MockitoExtension.class)
class MinioMediaStorageAdapterTest {

    @Mock
    private S3Client s3Client;

    private MinioMediaStorageAdapter adapter;

    private static final String BUCKET = "unihub-media";
    private static final String ENDPOINT = "http://localhost:9000";

    @BeforeEach
    void setUp() {
        adapter = new MinioMediaStorageAdapter(
            s3Client,
            BUCKET,
            ENDPOINT,
            null
        );
    }

    @Test
    @DisplayName(
        "Successfully uploads media to MinIO and returns StoredMedia metadata"
    )
    void shouldUploadMediaSuccessfully() {
        byte[] content = "image-bytes".getBytes();
        InputStream is = new ByteArrayInputStream(content);

        StoredMedia result = adapter.upload(
            is,
            content.length,
            "image/jpeg",
            MediaAssetType.COVER_IMAGE,
            ".jpg"
        );

        ArgumentCaptor<PutObjectRequest> putCaptor = ArgumentCaptor.forClass(
            PutObjectRequest.class
        );
        verify(s3Client).putObject(putCaptor.capture(), any(RequestBody.class));

        PutObjectRequest captured = putCaptor.getValue();
        assertThat(captured.bucket()).isEqualTo(BUCKET);
        assertThat(captured.key())
            .startsWith("workshops/covers/")
            .endsWith(".jpg");
        assertThat(captured.contentType()).isEqualTo("image/jpeg");
        assertThat(captured.contentLength()).isEqualTo((long) content.length);

        assertThat(result.fileKey()).isEqualTo(captured.key());
        assertThat(result.publicUrl()).isEqualTo(
            "http://localhost:9000/unihub-media/" + captured.key()
        );
        assertThat(result.assetType()).isEqualTo(MediaAssetType.COVER_IMAGE);
        assertThat(result.size()).isEqualTo(content.length);
        assertThat(result.contentType()).isEqualTo("image/jpeg");
    }

    @Test
    @DisplayName("Upload failure wraps S3 exception in InternalServerException")
    void shouldWrapS3ExceptionInInternalServerExceptionOnUpload() {
        byte[] content = "image-bytes".getBytes();
        InputStream is = new ByteArrayInputStream(content);

        when(
            s3Client.putObject(
                any(PutObjectRequest.class),
                any(RequestBody.class)
            )
        ).thenThrow(S3Exception.builder().message("Storage error").build());

        assertThatThrownBy(() ->
            adapter.upload(
                is,
                content.length,
                "image/jpeg",
                MediaAssetType.SPEAKER_AVATAR,
                ".png"
            )
        )
            .isInstanceOf(InternalServerException.class)
            .hasMessageContaining("Storage provider error during MinIO upload");
    }

    @Test
    @DisplayName("Successfully deletes object by fileKey")
    void shouldDeleteObjectByFileKey() {
        adapter.delete("workshops/covers/some-uuid.jpg");

        ArgumentCaptor<DeleteObjectRequest> deleteCaptor =
            ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client).deleteObject(deleteCaptor.capture());

        assertThat(deleteCaptor.getValue().bucket()).isEqualTo(BUCKET);
        assertThat(deleteCaptor.getValue().key()).isEqualTo(
            "workshops/covers/some-uuid.jpg"
        );
    }

    @Test
    @DisplayName("Delete with null or blank fileKey is a no-op")
    void shouldIgnoreNullOrBlankKeyOnDelete() {
        adapter.delete(null);
        adapter.delete("   ");

        verifyNoInteractions(s3Client);
    }

    @Test
    @DisplayName("Delete safely handles S3Exception without throwing to caller")
    void shouldTolerateS3ExceptionOnDelete() {
        doThrow(S3Exception.builder().message("S3 error").build())
            .when(s3Client)
            .deleteObject(any(DeleteObjectRequest.class));

        // Should not throw
        adapter.delete("workshops/covers/some-uuid.jpg");
        verify(s3Client).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    @DisplayName("Successfully deletes object by public URL")
    void shouldDeleteObjectByPublicUrl() {
        String publicUrl =
            "http://localhost:9000/unihub-media/speakers/avatars/speaker-1.png";

        adapter.deleteByUrl(publicUrl);

        ArgumentCaptor<DeleteObjectRequest> deleteCaptor =
            ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client).deleteObject(deleteCaptor.capture());

        assertThat(deleteCaptor.getValue().bucket()).isEqualTo(BUCKET);
        assertThat(deleteCaptor.getValue().key()).isEqualTo(
            "speakers/avatars/speaker-1.png"
        );
    }

    @Test
    @DisplayName("DeleteByUrl with external non-MinIO URL is a no-op")
    void shouldIgnoreExternalUrlOnDeleteByUrl() {
        adapter.deleteByUrl("https://example.com/external-avatar.jpg");
        adapter.deleteByUrl(null);
        adapter.deleteByUrl("   ");

        verifyNoInteractions(s3Client);
    }

    @Test
    @DisplayName("Successfully deletes multiple objects by public URLs")
    void shouldDeleteMultipleObjectsByPublicUrls() {
        List<String> urls = List.of(
            "http://localhost:9000/unihub-media/workshops/covers/cover-1.jpg",
            "http://localhost:9000/unihub-media/speakers/avatars/speaker-1.jpg",
            "https://external.com/ignored.jpg"
        );

        adapter.deleteAllByUrls(urls);

        verify(s3Client, times(2)).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    @DisplayName("DeleteAllByUrls with null or empty list is a no-op")
    void shouldIgnoreNullOrEmptyCollectionOnDeleteAllByUrls() {
        adapter.deleteAllByUrls(null);
        adapter.deleteAllByUrls(List.of());

        verifyNoInteractions(s3Client);
    }

    @Test
    @DisplayName(
        "extractFileKeyFromUrl accurately parses key from full public URL"
    )
    void shouldExtractFileKeyFromUrl() {
        String key = adapter.extractFileKeyFromUrl(
            "http://localhost:9000/unihub-media/workshops/covers/abc-123.jpg"
        );
        assertThat(key).isEqualTo("workshops/covers/abc-123.jpg");

        assertThat(
            adapter.extractFileKeyFromUrl("https://external.com/photo.jpg")
        ).isNull();
        assertThat(adapter.extractFileKeyFromUrl(null)).isNull();
        assertThat(adapter.extractFileKeyFromUrl("")).isNull();
    }
}
