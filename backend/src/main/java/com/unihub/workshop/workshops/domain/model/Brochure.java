package com.unihub.workshop.workshops.domain.model;

import java.time.OffsetDateTime;
import java.util.UUID;
import jakarta.persistence.PrePersist;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "brochures")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Brochure {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private UUID id;

    @PrePersist
    private void ensureId() {
        if (this.id == null) {
            this.id = UUID.randomUUID();
        }
    }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workshop_id", nullable = false)
    @Setter(AccessLevel.PACKAGE)
    private Workshop workshop;

    @Column(name = "storage_key", nullable = false)
    @Setter(AccessLevel.NONE)
    private String storageKey;

    @Column(name = "file_name")
    @Setter(AccessLevel.NONE)
    private String fileName;

    @Column(name = "mime_type")
    @Setter(AccessLevel.NONE)
    private String mimeType;

    @Column(name = "size_bytes")
    @Setter(AccessLevel.NONE)
    private Long sizeBytes;

    @Column(name = "status", nullable = false)
    @Setter(AccessLevel.NONE)
    private String status = "UPLOADED";

    @Column(name = "file_url", nullable = false)
    @Setter(AccessLevel.NONE)
    private String fileUrl;

    @Column(name = "uploaded_at", nullable = false)
    @Setter(AccessLevel.NONE)
    private OffsetDateTime uploadedAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private OffsetDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    @Setter(AccessLevel.NONE)
    private OffsetDateTime updatedAt;

    public Brochure(String storageKey, String fileName, String mimeType, Long sizeBytes, String fileUrl,
            OffsetDateTime uploadedAt) {
        this.storageKey = storageKey;
        this.fileName = fileName;
        this.mimeType = mimeType;
        this.sizeBytes = sizeBytes;
        this.fileUrl = fileUrl;
        this.uploadedAt = uploadedAt;
    }
}
