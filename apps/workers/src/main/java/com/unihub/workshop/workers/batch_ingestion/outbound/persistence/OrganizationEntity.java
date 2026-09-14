package com.unihub.workshop.workers.batch_ingestion.outbound.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "organizations")
@Getter
@Setter
public class OrganizationEntity {

    @Id
    private UUID id;

    private String name;
    private String slug;
    private String status;
}
