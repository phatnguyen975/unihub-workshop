package com.unihub.workshop.iam.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.DynamicUpdate;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "student_directory")
@Getter
@Setter
@DynamicUpdate
public class StudentDirectoryJpaEntity {

    @Id
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "student_number", nullable = false)
    private String studentNumber;

    @Column(name = "email", nullable = false)
    private String email;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(name = "faculty")
    private String faculty;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "source_file_key")
    private String sourceFileKey;

    @Column(name = "imported_at", nullable = false)
    private Instant importedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
