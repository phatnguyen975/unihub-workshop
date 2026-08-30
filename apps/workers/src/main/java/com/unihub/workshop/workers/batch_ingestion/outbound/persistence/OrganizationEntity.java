package com.unihub.workshop.workers.batch_ingestion.outbound.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "organizations")
@Getter
@Setter
public class OrganizationEntity {

    @Id
    private UUID id;
    private String name;
    private String slug;
}
