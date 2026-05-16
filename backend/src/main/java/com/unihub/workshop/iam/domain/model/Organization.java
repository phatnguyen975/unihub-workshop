package com.unihub.workshop.iam.domain.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "organizations")
@Getter
@NoArgsConstructor
public class Organization {

    @Id
    @Column(name = "id", nullable = false)
    @Setter
    private UUID id;

    @Column(name = "name", nullable = false)
    @Setter
    private String name;

    @Column(name = "description")
    @Setter
    private String description;

    @Column(name = "address")
    @Setter
    private String address;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Setter
    private OrganizationStatus status;

    @Column(name = "organizer_id", nullable = false)
    @Setter
    private UUID organizerId;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
