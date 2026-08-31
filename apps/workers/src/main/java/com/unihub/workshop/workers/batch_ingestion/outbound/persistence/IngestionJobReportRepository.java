package com.unihub.workshop.workers.batch_ingestion.outbound.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface IngestionJobReportRepository extends JpaRepository<IngestionJobReportEntity, UUID> {
}
