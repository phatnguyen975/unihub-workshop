package com.unihub.workshop.workshops.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.DynamicUpdate;

@Entity
@Table(name = "workshop_assignments")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@DynamicUpdate
public class WorkshopAssignmentJpaEntity {

    @Id
    private UUID id;

    @Column(name = "workshop_id", nullable = false)
    private UUID workshopId;

    @Column(name = "staff_user_id", nullable = false)
    private UUID staffUserId;

    @Column(name = "assigned_by", nullable = false)
    private UUID assignedBy;

    @Column(name = "assigned_at", nullable = false)
    private Instant assignedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "updated_by", nullable = false)
    private UUID updatedBy;
}
