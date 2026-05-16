package com.unihub.workshop.workshops.domain.model;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.PrePersist;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "workshops")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Workshop {

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

    @Column(name = "organizer_id", nullable = false)
    @Setter(AccessLevel.NONE)
    private UUID organizerId;

    @Column(name = "title", nullable = false)
    @Setter
    private String title;

    @Column(name = "description")
    @Setter
    private String description;

    @Column(name = "location", nullable = false)
    @Setter
    private String location;

    @Column(name = "venue")
    @Setter
    private String venue;

    @Column(name = "room")
    @Setter
    private String room;

    @Column(name = "room_layout_key")
    @Setter
    private String roomLayoutKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Setter
    private WorkshopStatus status = WorkshopStatus.DRAFT;

    @Version
    @Column(name = "version", nullable = false)
    @Setter(AccessLevel.NONE)
    private int version = 0;

    @Column(name = "capacity_total", nullable = false)
    @Setter
    private int capacityTotal = 0;

    @Column(name = "capacity_remaining", nullable = false)
    @Setter
    private int capacityRemaining = 0;

    @Column(name = "price_amount", nullable = false)
    @Setter
    private BigDecimal priceAmount = BigDecimal.ZERO;

    @Column(name = "currency", columnDefinition = "bpchar", length = 3)
    @Setter
    private String currency;

    @Column(name = "start_time", nullable = false)
    @Setter
    private OffsetDateTime startTime;

    @Column(name = "end_time", nullable = false)
    @Setter
    private OffsetDateTime endTime;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private OffsetDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    @Setter(AccessLevel.NONE)
    private OffsetDateTime updatedAt;

    @OneToMany(mappedBy = "workshop", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private List<WorkshopSpeaker> speakers = new ArrayList<>();

    @OneToMany(mappedBy = "workshop", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private List<WorkshopAssignment> assignments = new ArrayList<>();

    @OneToMany(mappedBy = "workshop", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private List<Brochure> brochures = new ArrayList<>();

    public Workshop(UUID organizerId, String title, String location, OffsetDateTime startTime, OffsetDateTime endTime) {
        this.organizerId = organizerId;
        this.title = title;
        this.location = location;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public List<WorkshopSpeaker> getSpeakers() {
        return Collections.unmodifiableList(speakers);
    }

    public List<WorkshopAssignment> getAssignments() {
        return Collections.unmodifiableList(assignments);
    }

    public List<Brochure> getBrochures() {
        return Collections.unmodifiableList(brochures);
    }

    // Domain methods to manage aggregate children
    public void addSpeaker(WorkshopSpeaker speaker) {
        speaker.setWorkshop(this);
        this.speakers.add(speaker);
    }

    public void removeSpeaker(WorkshopSpeaker speaker) {
        this.speakers.remove(speaker);
        speaker.setWorkshop(null);
    }

    public void addAssignment(WorkshopAssignment assignment) {
        assignment.setWorkshop(this);
        this.assignments.add(assignment);
    }

    public void removeAssignment(WorkshopAssignment assignment) {
        this.assignments.remove(assignment);
        assignment.setWorkshop(null);
    }

    public void addBrochure(Brochure brochure) {
        brochure.setWorkshop(this);
        this.brochures.add(brochure);
    }

    public void removeBrochure(Brochure brochure) {
        this.brochures.remove(brochure);
        brochure.setWorkshop(null);
    }
}
