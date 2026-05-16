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
@Table(name = "workshop_speakers")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorkshopSpeaker {

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

    @Column(name = "speaker_name", nullable = false)
    @Setter
    private String speakerName;

    @Column(name = "speaker_bio")
    @Setter
    private String speakerBio;

    @Column(name = "sort_order", nullable = false)
    @Setter
    private int sortOrder = 0;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private OffsetDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    @Setter(AccessLevel.NONE)
    private OffsetDateTime updatedAt;

    public WorkshopSpeaker(String speakerName, String speakerBio, int sortOrder) {
        this.speakerName = speakerName;
        this.speakerBio = speakerBio;
        this.sortOrder = sortOrder;
    }
}
